package com.riyura.backend.modules.identity.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.modules.identity.dto.recomendation.GeminiRecommendationItem;
import com.riyura.backend.modules.identity.model.Recommendation;
import com.riyura.backend.modules.identity.model.WatchHistory;
import com.riyura.backend.modules.identity.model.Watchlist;
import com.riyura.backend.modules.identity.repository.RecommendationRepository;
import com.riyura.backend.modules.identity.repository.WatchHistoryRepository;
import com.riyura.backend.modules.identity.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRepository recommendationRepo;
    private final WatchHistoryRepository historyRepo;
    private final WatchlistRepository watchlistRepo;
    private final GeminiApiKeyService apiKeyService;
    private final TmdbClient tmdbClient;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ObjectMapper enumMapper = JsonMapper.builder()
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
            .build();
    private final RestTemplate geminiRestTemplate = buildGeminiRestTemplate();

    @Value("${tmdb.api.key}")
    private String tmdbApiKey;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=";

    // Virtual-Thread safe rate limiter. Limits concurrent TMDB outbound requests
    // strictly to 3 at a time.
    private final Semaphore tmdbRateLimiter = new Semaphore(3);
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private RestTemplate buildGeminiRestTemplate() {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(60));
        return new RestTemplate(factory);
    }

    // Returns cached DB recommendations when forceRefresh is false (the default).
    // Only calls Gemini when refresh=true is explicitly requested.
    public List<Recommendation> getRecommendations(UUID userId, boolean forceRefresh) {
        if (!forceRefresh) {
            List<Recommendation> existing = recommendationRepo.findByUserIdOrderByGeneratedAtDesc(userId);
            log.info("Serving {} recommendations from DB for user: {}", existing.size(), userId);
            return existing;
        }

        log.info("Generating new recommendations for user: {}", userId);

        // Fetch Analytics Context
        List<WatchHistory> fullHistory = historyRepo.findByUserIdOrderByWatchedAtDesc(userId, PageRequest.of(0, 5000));
        List<Watchlist> topWatchlist = watchlistRepo.findByUserIdOrderByAddedAtDesc(userId, PageRequest.of(0, 8));

        // Build Gemini Prompt with rich context (entire history + watchlist)
        String prompt = buildGeminiPrompt(fullHistory, topWatchlist);
        String decryptedKey = apiKeyService.getDecryptedKeyForUser(userId);

        // Call Gemini
        List<GeminiRecommendationItem> geminiItems = callGeminiApi(prompt, decryptedKey);

        if (geminiItems.isEmpty()) {
            throw new RuntimeException("AI failed to generate recommendations");
        }

        // Pass the virtualThreadExecutor as the second argument
        List<CompletableFuture<Recommendation>> hydrationFutures = geminiItems.stream()
                .map(item -> CompletableFuture.supplyAsync(() -> hydrateWithTmdbRateLimited(item, userId),
                        virtualThreadExecutor))
                .toList();

        // Collect all hydration results and log how many passed vs total so we can
        // diagnose issues where fewer items than expected are hydrated.
        List<Recommendation> hydrated = hydrationFutures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .filter(r -> r.getMediaType() != null)
                .collect(Collectors.toList());

        log.info("🎬 TMDB hydration complete: {}/{} items succeeded for user: {}",
                hydrated.size(), geminiItems.size(), userId);

        // De-duplicate by tmdbId — the composite PK is (userId, tmdbId), so a user
        // can only hold one recommendation per TMDB entry regardless of type.
        List<Recommendation> validRecommendations = hydrated.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                Recommendation::getTmdbId,
                                r -> r,
                                (existing, duplicate) -> existing,
                                LinkedHashMap::new),
                        m -> new ArrayList<>(m.values())));

        // Background DB Save on virtual threads — fire-and-forget with full transaction
        // integrity. A DataIntegrityViolationException here means a concurrent request
        // for the same user already won the race; we log and skip silently.
        if (!validRecommendations.isEmpty()) {
            CompletableFuture.runAsync(() -> {
                try {
                    transactionTemplate.execute(status -> {
                        recommendationRepo.deleteByUserId(userId);
                        recommendationRepo.saveAll(validRecommendations);
                        return null;
                    });
                    log.info("✅ Background DB save complete for user: {}", userId);
                } catch (DataIntegrityViolationException e) {
                    log.warn(
                            "⚠️ Concurrent recommendation save detected for user: {} — skipping (another request already saved)",
                            userId);
                } catch (Exception e) {
                    log.error("❌ Failed to save recommendations to DB in background", e);
                }
            }, virtualThreadExecutor);
        }

        return validRecommendations;
    }

    // Builds a detailed prompt for Gemini using the user's entire watch history and
    // top watchlist items to provide rich context for personalized recommendations.
    private String buildGeminiPrompt(List<WatchHistory> history, List<Watchlist> watchlist) {
        long totalWatchTimeSec = history.stream().mapToLong(h -> h.getDurationSec() != null ? h.getDurationSec() : 0)
                .sum();
        long totalHours = totalWatchTimeSec / 3600;
        String lastWatched = history.isEmpty() ? "None" : history.get(0).getTitle();

        StringBuilder historyTitles = new StringBuilder();
        for (WatchHistory h : history) {
            long mins = (h.getDurationSec() != null ? h.getDurationSec() : 0) / 60;
            historyTitles.append(h.getTitle()).append(" (").append(h.getMediaType().name().toLowerCase())
                    .append(") - Watched for ").append(mins).append(" mins\n");
        }

        String watchlistTitles = watchlist.stream()
                .map(w -> w.getTitle() + " (" + w.getMediaType().name().toLowerCase() + ")")
                .collect(Collectors.joining(", "));

        return String.format(
                """
                        You are an elite cinematic curator. Your goal is to provide highly personalized recommendations by analyzing the user's viewing history.

                        **USER ANALYTICS:**
                        - Total Watch Time: ~%d hours
                        - Recent Obsession: %s
                        - Context: Evaluate the entirety of the provided history to find subtle psychographic patterns.

                        **INPUT DATA (ENTIRE HISTORY):**
                        %s

                        **WATCHLIST (TOP 8):**
                        %s

                        **OUTPUT REQUIREMENTS:**
                        Generate EXACTLY 8 recommendations divided strictly as follows:
                        - 3 Live-action Movies
                        - 3 Live-action TV Shows
                        - 2 Anime (Japanese Animation)

                        **FORMATTING RULES:**
                        - Return ONLY a JSON array with exactly 8 items.
                        - "type" MUST be exactly one of: "Movie" or "TV". (If recommending an Anime series, classify its type strictly as "TV". If an Anime film, classify as "Movie").
                        - No markdown formatting. Just raw JSON.

                        **JSON SCHEMA:**
                        [{"title": "String", "type": "Movie|TV", "reason": "Specific psychological connection to history", "genre": "String"}]
                        """,
                totalHours, lastWatched, historyTitles.toString().trim(),
                watchlistTitles.isEmpty() ? "None" : watchlistTitles);
    }

    // Calls Gemini API with the constructed prompt and returns a list of
    // recommendation items. Retries up to 3 times with exponential backoff on all
    // transient errors: 503 Service Unavailable, 429 Too Many Requests, and
    // read timeouts. Hard client errors (400, 401, 403) are not retried.
    private List<GeminiRecommendationItem> callGeminiApi(String prompt, String apiKey) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

        Map<String, Object> config = new HashMap<>();
        config.put("temperature", 0.8);
        config.put("responseMimeType", "application/json");
        requestBody.put("generationConfig", config);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String url = GEMINI_API_URL + apiKey;
        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String responseStr = geminiRestTemplate.postForObject(url, entity, String.class);

                JsonNode root = objectMapper.readTree(responseStr);
                String rawJson = root.path("candidates").get(0).path("content").path("parts").get(0).path("text")
                        .asText();

                if (rawJson.startsWith("```")) {
                    rawJson = rawJson.replaceAll("```json", "").replaceAll("```", "").trim();
                }

                List<GeminiRecommendationItem> parsed = enumMapper.readValue(
                        rawJson,
                        new TypeReference<List<GeminiRecommendationItem>>() {
                        });

                return parsed.stream()
                        .filter(item -> item.getType() != null)
                        .filter(item -> item.getTitle() != null && !item.getTitle().isBlank())
                        .toList();

            } catch (HttpServerErrorException.ServiceUnavailable | HttpClientErrorException.TooManyRequests e) {
                // 503 = Gemini overloaded; 429 = rate limited — both are transient, retry
                if (attempt < maxAttempts) {
                    long backoffMs = 2000L * attempt; // 2s, 4s
                    log.warn("Gemini transient error ({}) — retrying in {}ms (attempt {}/{})",
                            e.getStatusCode(), backoffMs, attempt + 1, maxAttempts);
                    sleepUninterruptibly(backoffMs);
                    continue;
                }
                log.error("Gemini API unavailable after {} attempts: {}", maxAttempts, e.getMessage());
                throw new RuntimeException("AI service is temporarily unavailable. Please try again shortly.", e);

            } catch (ResourceAccessException e) {
                boolean isTimeout = e.getCause() instanceof HttpTimeoutException
                        || (e.getMessage() != null && e.getMessage().contains("Request cancelled"));
                if (isTimeout && attempt < maxAttempts) {
                    log.warn("Gemini request timed out, retrying (attempt {}/{})", attempt + 1, maxAttempts);
                    continue;
                }
                log.error("Gemini API timeout/network error: {}", e.getMessage(), e);
                throw new RuntimeException("AI request timed out. Please retry.", e);

            } catch (Exception e) {
                log.error("Gemini API Error: {}", e.getMessage(), e);
                throw new RuntimeException("AI processing failed", e);
            }
        }

        throw new RuntimeException("AI processing failed");
    }

    private void sleepUninterruptibly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // Hydrates a Gemini recommendation item with TMDB data, respecting the rate
    // limit. If TMDB returns 429, it will retry with exponential backoff up to 3
    // attempts before giving up on that item.
    private Recommendation hydrateWithTmdbRateLimited(GeminiRecommendationItem item, UUID userId) {
        try {
            tmdbRateLimiter.acquire(); // Strictly ensures only 3 requests happen at once
            return performTmdbHydrationWith429Retry(item, userId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("TMDB Rate Limiter thread interrupted");
            return null;
        } finally {
            tmdbRateLimiter.release();
        }
    }

    // Performs the actual TMDB hydration with built-in retry logic for handling 429
    // Too Many Requests responses.
    private Recommendation performTmdbHydrationWith429Retry(GeminiRecommendationItem item, UUID userId) {
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String searchType = item.getType() == MediaType.Movie ? "movie" : "tv";
                String searchUrl = String.format(
                        "https://api.themoviedb.org/3/search/%s?api_key=%s&query=%s",
                        searchType, tmdbApiKey, java.net.URLEncoder.encode(item.getTitle(), "UTF-8"));

                String searchResStr = tmdbClient.fetchWithRetry(searchUrl, String.class);
                JsonNode searchRes = objectMapper.readTree(searchResStr);
                JsonNode results = searchRes.path("results");

                if (results.isEmpty()) {
                    log.warn("TMDB 404 for AI Recommendation: {}", item.getTitle());
                    return null;
                }

                JsonNode firstMatch = results.get(0);
                Long tmdbId = firstMatch.path("id").asLong();

                Recommendation.RecommendationBuilder builder = Recommendation.builder()
                        .userId(userId)
                        .tmdbId(tmdbId)
                        .title(firstMatch.has("title") ? firstMatch.path("title").asText()
                                : firstMatch.path("name").asText())
                        .mediaType(item.getType())
                        .posterPath(firstMatch.path("poster_path").asText(null))
                        .voteAverage(firstMatch.path("vote_average").asDouble(0.0))
                        .reason(item.getReason())
                        .genre(item.getGenre());

                String releaseDateStr = firstMatch.has("release_date") ? firstMatch.path("release_date").asText(null)
                        : firstMatch.path("first_air_date").asText(null);
                if (releaseDateStr != null && !releaseDateStr.isEmpty() && !releaseDateStr.equals("null")) {
                    builder.releaseDate(LocalDate.parse(releaseDateStr));
                }

                if ("tv".equals(searchType)) {
                    String detailsUrl = String.format(
                            "https://api.themoviedb.org/3/tv/%d?api_key=%s",
                            tmdbId, tmdbApiKey);
                    try {
                        String detailsResStr = tmdbClient.fetchWithRetry(detailsUrl, String.class);
                        JsonNode detailsRes = objectMapper.readTree(detailsResStr);
                        builder.numberOfSeasons(detailsRes.path("number_of_seasons").asInt(0));
                        builder.numberOfEpisodes(detailsRes.path("number_of_episodes").asInt(0));
                    } catch (Exception e) {
                        log.warn("Failed to fetch extra TV details for {}", tmdbId);
                    }
                }
                return builder.build();

            } catch (HttpClientErrorException.TooManyRequests e) {
                log.warn("TMDB 429 Too Many Requests. Retrying... (Attempt {}/{})", attempt, maxAttempts);
                if (attempt == maxAttempts)
                    return null; // Give up on this item to save the rest
                try {
                    Thread.sleep(800L * attempt); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            } catch (Exception e) {
                log.error("TMDB Hydration failed for title: {}", item.getTitle(), e);
                return null;
            }
        }
        return null;
    }
}