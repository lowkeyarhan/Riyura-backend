package com.riyura.backend.modules.identity.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.*;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

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

    @Value("${tmdb.api.key}")
    private String tmdbApiKey;

    private static final String GEMINI_MODEL = "gemini-3.1-flash-lite-preview";
    private final Semaphore tmdbRateLimiter = new Semaphore(4);
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // Returns the recommendations for the user, if they exist in the database. If
    // they don't, it will generate new recommendations.
    public List<Recommendation> getRecommendations(UUID userId, boolean forceRefresh) {
        if (!forceRefresh) {
            List<Recommendation> existing = recommendationRepo.findByUserIdOrderByGeneratedAtDesc(userId);
            log.info("Serving {} recommendations from DB for user: {}", existing.size(), userId);
            return existing;
        }

        log.info("Generating new recommendations via RAG pipeline for user: {}", userId);

        // Use a small, recent slice of history as seed items for TMDB /recommendations
        List<WatchHistory> seedHistory = historyRepo.findByUserIdOrderByWatchedAtDesc(userId, PageRequest.of(0, 8));
        List<Watchlist> recentWatchlist = watchlistRepo.findByUserIdOrderByAddedAtDesc(userId, PageRequest.of(0, 8));

        if (seedHistory.isEmpty()) {
            throw new RuntimeException("Watch a few titles first so we can tailor recommendations for you.");
        }

        Map<Long, CandidateItem> candidatePool = buildCandidatePool(seedHistory);

        if (candidatePool.isEmpty()) {
            throw new RuntimeException("Could not fetch recommendation candidates from TMDB. Please try again.");
        }

        log.info("Candidate pool built: {} unique items for user: {}", candidatePool.size(), userId);

        String prompt = buildRagPrompt(seedHistory, recentWatchlist, candidatePool);
        String decryptedKey = apiKeyService.getDecryptedKeyForUser(userId);
        List<GeminiRecommendationItem> selections = callGeminiApi(prompt, decryptedKey);

        if (selections.isEmpty()) {
            throw new RuntimeException("AI failed to select recommendations. Please try again.");
        }

        List<Recommendation> results = selections.stream()
                .map(sel -> {
                    if (sel.getTmdbId() == null)
                        return null;
                    CandidateItem meta = candidatePool.get(sel.getTmdbId());
                    if (meta == null) {
                        log.warn("Gemini selected tmdb_id {} which is not in candidate pool — skipping",
                                sel.getTmdbId());
                        return null;
                    }
                    return Recommendation.builder()
                            .userId(userId)
                            .tmdbId(meta.tmdbId())
                            .title(meta.title())
                            .mediaType(meta.mediaType())
                            .releaseDate(meta.releaseDate())
                            .reason(sel.getReason())
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(Recommendation::getTmdbId, r -> r, (existing, dup) -> existing,
                                LinkedHashMap::new),
                        m -> new ArrayList<>(m.values())));

        log.info("RAG pipeline complete: {}/{} selections enriched for user: {}",
                results.size(), selections.size(), userId);

        if (!results.isEmpty()) {
            CompletableFuture.runAsync(() -> {
                try {
                    transactionTemplate.execute(status -> {
                        recommendationRepo.deleteByUserId(userId);
                        recommendationRepo.saveAll(results);
                        return null;
                    });
                    log.info("Background DB save complete for user: {}", userId);
                } catch (DataIntegrityViolationException e) {
                    log.warn("Concurrent recommendation save for user: {} — skipping", userId);
                } catch (Exception e) {
                    log.error("Failed to save recommendations in background", e);
                }
            }, virtualThreadExecutor);
        }

        return results;
    }

    // For each seed history item, fetches TMDB /recommendations in parallel.
    private Map<Long, CandidateItem> buildCandidatePool(List<WatchHistory> seedHistory) {
        List<CompletableFuture<List<CandidateItem>>> futures = seedHistory.stream()
                .map(h -> CompletableFuture.supplyAsync(
                        () -> fetchTmdbRecommendations(h.getTmdbId(), h.getMediaType()),
                        virtualThreadExecutor))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> futures.stream()
                        .flatMap(f -> {
                            try {
                                return f.join().stream();
                            } catch (Exception e) {
                                return java.util.stream.Stream.empty();
                            }
                        })
                        .collect(Collectors.toMap(
                                CandidateItem::tmdbId,
                                c -> c,
                                (existing, dup) -> existing,
                                LinkedHashMap::new)))
                .join();
    }

    // Calls TMDB /movie/{id}/recommendations or /tv/{id}/recommendations.
    private List<CandidateItem> fetchTmdbRecommendations(long tmdbId, MediaType mediaType) {
        String type = mediaType == MediaType.Movie ? "movie" : "tv";
        String url = String.format(
                "https://api.themoviedb.org/3/%s/%d/recommendations?api_key=%s&language=en-US&page=1",
                type, tmdbId, tmdbApiKey);

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                tmdbRateLimiter.acquire();
                String raw;
                try {
                    raw = tmdbClient.fetchWithRetry(url, String.class);
                } finally {
                    tmdbRateLimiter.release();
                }

                JsonNode results = objectMapper.readTree(raw).path("results");
                List<CandidateItem> items = new ArrayList<>();
                for (JsonNode node : results) {
                    CandidateItem item = parseTmdbNode(node, mediaType);
                    if (item != null)
                        items.add(item);
                }
                return items;

            } catch (HttpClientErrorException.TooManyRequests e) {
                long backoff = 1500L * attempt; // 1.5s, 3s, 4.5s
                log.warn("TMDB 429 on /recommendations for id={} (attempt {}/{}) — backing off {}ms",
                        tmdbId, attempt, maxAttempts, backoff);
                if (attempt == maxAttempts)
                    return Collections.emptyList();
                sleepUninterruptibly(backoff);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Collections.emptyList();
            } catch (Exception e) {
                log.warn("TMDB /recommendations failed for id={}: {}", tmdbId, e.getMessage());
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    // Parses a single TMDB result node into a CandidateItem. Returns null if unfit.
    private CandidateItem parseTmdbNode(JsonNode node, MediaType mediaType) {
        long id = node.path("id").asLong(0);
        if (id == 0)
            return null;

        String title = node.has("title") ? node.path("title").asText(null)
                : node.path("name").asText(null);
        if (title == null || title.isBlank())
            return null;

        String overview = node.path("overview").asText("");
        String genreIds = "";
        if (node.has("genre_ids")) {
            List<String> ids = new ArrayList<>();
            node.path("genre_ids").forEach(g -> ids.add(g.asText()));
            genreIds = String.join(",", ids);
        }

        String releaseDateStr = node.has("release_date")
                ? node.path("release_date").asText(null)
                : node.path("first_air_date").asText(null);
        LocalDate releaseDate = null;
        if (releaseDateStr != null && !releaseDateStr.isBlank() && !releaseDateStr.equals("null")) {
            try {
                releaseDate = LocalDate.parse(releaseDateStr);
            } catch (Exception ignored) {
            }
        }

        return new CandidateItem(id, title, mediaType, releaseDate, genreIds, overview);
    }

    // Builds the RAG prompt.
    private String buildRagPrompt(List<WatchHistory> history, List<Watchlist> watchlist,
            Map<Long, CandidateItem> pool) {

        StringBuilder historyLines = new StringBuilder();
        for (WatchHistory h : history) {
            long mins = (h.getDurationSec() != null ? h.getDurationSec() : 0) / 60;
            historyLines.append("- ").append(h.getTitle())
                    .append(" (").append(h.getMediaType().name().toLowerCase())
                    .append(", ").append(mins).append(" mins)\n");
        }

        String watchlistLine = watchlist.stream()
                .map(w -> w.getTitle() + " (" + w.getMediaType().name().toLowerCase() + ")")
                .collect(Collectors.joining(", "));

        StringBuilder poolJson = new StringBuilder("[");
        boolean first = true;
        for (CandidateItem c : pool.values()) {
            if (!first)
                poolJson.append(",");
            first = false;
            int year = c.releaseDate() != null ? c.releaseDate().getYear() : 0;
            poolJson.append(String.format(
                    "{\"tmdb_id\":%d,\"title\":\"%s\",\"type\":\"%s\",\"year\":%d,\"genre_ids\":\"%s\"}",
                    c.tmdbId(),
                    c.title().replace("\"", "'"),
                    c.mediaType().name().toLowerCase(),
                    year,
                    c.genreIds()));
        }
        poolJson.append("]");

        return String.format("""
                You are a film and TV curator. Select exactly 8 recommendations from the CANDIDATE POOL for this user.

                USER RECENTLY WATCHED:
                %s
                USER WATCHLIST: %s

                CANDIDATE POOL (select ONLY from these — do not invent new items):
                %s

                SELECTION RULES:
                - Pick exactly 3 movies, 3 TV shows, and 2 anime (anime are TV type from Japan).
                - Do NOT pick anything already in the user's watch history or watchlist.
                - Prioritise variety of genres — avoid 8 items that are all the same genre.
                - Write a concise, personalised reason (1 sentence) explaining why it suits this user.

                Return ONLY a JSON array with exactly 8 objects. No markdown, no explanation.
                Schema: [{"tmdb_id": <integer from pool>, "reason": "<string>"}]
                """,
                historyLines.toString().trim(),
                watchlistLine.isEmpty() ? "None" : watchlistLine,
                poolJson);
    }

    // Calls Gemini via official SDK.
    private List<GeminiRecommendationItem> callGeminiApi(String prompt, String apiKey) {
        Schema responseSchema = Schema.builder()
                .type(Type.Known.ARRAY)
                .items(Schema.builder()
                        .type(Type.Known.OBJECT)
                        .properties(Map.of(
                                "tmdb_id", Schema.builder().type(Type.Known.INTEGER).build(),
                                "reason", Schema.builder().type(Type.Known.STRING).build()))
                        .required(List.of("tmdb_id", "reason"))
                        .build())
                .build();

        GenerateContentConfig config = GenerateContentConfig.builder()
                .thinkingConfig(ThinkingConfig.builder()
                        .thinkingBudget(0) // LOW thinking: fastest, 0 = disabled
                        .build())
                .responseMimeType("application/json")
                .responseSchema(responseSchema)
                .build();

        List<Content> contents = List.of(
                Content.builder()
                        .role("user")
                        .parts(List.of(Part.fromText(prompt)))
                        .build());

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try (Client client = Client.builder().apiKey(apiKey).build();
                    ResponseStream<GenerateContentResponse> stream = client.models.generateContentStream(GEMINI_MODEL,
                            contents, config)) {

                StringBuilder raw = new StringBuilder();
                for (GenerateContentResponse res : stream) {
                    if (res.candidates().isEmpty())
                        continue;
                    Optional<Content> content = res.candidates().get().get(0).content();
                    if (content.isEmpty() || content.get().parts().isEmpty())
                        continue;
                    for (Part part : content.get().parts().get()) {
                        part.text().ifPresent(raw::append);
                    }
                }

                String rawJson = raw.toString().trim();
                if (rawJson.startsWith("```")) {
                    rawJson = rawJson.replaceAll("```json", "").replaceAll("```", "").trim();
                }

                List<GeminiRecommendationItem> parsed = objectMapper.readValue(
                        rawJson, new TypeReference<>() {
                        });

                log.info("Gemini returned {} selections (attempt {})", parsed.size(), attempt);
                return parsed.stream()
                        .filter(item -> item.getTmdbId() != null)
                        .toList();

            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt < maxAttempts) {
                    long backoff = 2000L * attempt;
                    log.warn("Gemini 429 — retrying in {}ms (attempt {}/{})", backoff, attempt + 1, maxAttempts);
                    sleepUninterruptibly(backoff);
                } else {
                    throw new RuntimeException("AI service is temporarily unavailable. Please try again shortly.", e);
                }
            } catch (Exception e) {
                if (attempt < maxAttempts) {
                    log.warn("Gemini error (attempt {}/{}): {} — retrying", attempt, maxAttempts, e.getMessage());
                    sleepUninterruptibly(2000L * attempt);
                } else {
                    log.error("Gemini API failed after {} attempts", maxAttempts, e);
                    throw new RuntimeException("AI processing failed", e);
                }
            }
        }
        throw new RuntimeException("AI processing failed after retries");
    }

    private void sleepUninterruptibly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // Represents one item in the TMDB candidate pool.
    private record CandidateItem(
            long tmdbId,
            String title,
            MediaType mediaType,
            LocalDate releaseDate,
            String genreIds,
            String overview) {
    }
}