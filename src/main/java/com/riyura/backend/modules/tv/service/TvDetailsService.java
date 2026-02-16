package com.riyura.backend.modules.tv.service;

import com.riyura.backend.common.dto.CastDetailsResponse;
import com.riyura.backend.common.dto.MediaGridResponse;
import com.riyura.backend.common.dto.TmdbTrendingResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.tv.dto.TvShowDetails;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TvDetailsService {

    private static final int TMDB_MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 150L;
    private static final int SIMILAR_LIMIT = 6;

    private final RestTemplate restTemplate;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    @Value("${tmdb.image-base-url}")
    private String imageBaseUrl;

    // Get TV Show Details by ID
    public TvShowDetails getTvDetails(String id) {
        String detailsUrl = String.format("%s/tv/%s?api_key=%s&language=en-US", baseUrl, id, apiKey);
        String creditsUrl = String.format("%s/tv/%s/credits?api_key=%s&language=en-US", baseUrl, id, apiKey);

        try {
            // Fetch details
            CompletableFuture<TvShowDetails> detailsTask = CompletableFuture
                    .supplyAsync(() -> fetchWithRetry(detailsUrl, TvShowDetails.class));
            // Fetch casts
            CompletableFuture<CreditsResponse> creditsTask = CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            return fetchWithRetry(creditsUrl, CreditsResponse.class);
                        } catch (Exception e) {
                            return null;
                        }
                    });

            TvShowDetails details = detailsTask.join();
            CreditsResponse credits = creditsTask.join();

            // Set casts in details
            if (details != null) {
                details.setCasts(
                        credits != null && credits.getCast() != null ? credits.getCast() : Collections.emptyList());
            }

            return details;
        } catch (Exception e) {
            System.err.println("Error fetching TV details for ID " + id + ": " + rootMessage(e));
            return null;
        }
    }

    // Get similar TV shows by ID
    public List<MediaGridResponse> getSimilarTvShows(String id) {
        String similarUrl = String.format("%s/tv/%s/similar?api_key=%s&language=en-US&page=1", baseUrl, id, apiKey);

        try {
            // Fetch similar TV shows
            TmdbTrendingResponse response = fetchWithRetry(similarUrl, TmdbTrendingResponse.class);
            if (response == null || response.getResults() == null) {
                return Collections.emptyList();
            }

            // Sort by vote average (descending) and limit to SIMILAR_LIMIT
            return response.getResults().stream()
                    .sorted(Comparator.comparing(
                            TmdbTrendingResponse.TmdbItem::getVoteAverage,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(SIMILAR_LIMIT)
                    .map(this::mapSimilarTvToDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Error fetching similar TV shows for ID " + id + ": " + rootMessage(e));
            return Collections.emptyList();
        }
    }

    // Helper method to map TMDB item to MediaGridResponse DTO
    private MediaGridResponse mapSimilarTvToDTO(TmdbTrendingResponse.TmdbItem item) {
        MediaGridResponse dto = new MediaGridResponse();
        dto.setTmdbId(item.getId());
        dto.setTitle(item.getName());
        dto.setYear(extractYear(item.getFirstAirDate()));
        dto.setMediaType(MediaType.TV);

        if (item.getPosterPath() != null && !item.getPosterPath().isEmpty()) {
            dto.setPosterUrl(imageBaseUrl + item.getPosterPath());
        }

        return dto;
    }

    // Helper method to extract year from date string
    private String extractYear(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }

        try {
            return String.valueOf(LocalDate.parse(dateString).getYear());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // Helper method to fetch data with retry logic for ResourceAccessException
    private <T> T fetchWithRetry(String url, Class<T> type) {
        ResourceAccessException lastResourceException = null;

        for (int attempt = 1; attempt <= TMDB_MAX_RETRIES; attempt++) {
            try {
                return restTemplate.getForObject(url, type);
            } catch (ResourceAccessException e) {
                lastResourceException = e;
                if (attempt == TMDB_MAX_RETRIES) {
                    throw e;
                }
                sleepBeforeRetry();
            } catch (RestClientException e) {
                throw e;
            }
        }

        throw lastResourceException;
    }

    // Helper method to sleep before retrying
    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // Helper method to get root cause message from exception
    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    // Inner class to map credits response
    @Data
    private static class CreditsResponse {
        private List<CastDetailsResponse> cast;
    }
}
