package com.riyura.backend.modules.tv.service;

import com.riyura.backend.common.dto.CastDetailsResponse;
import com.riyura.backend.modules.tv.dto.TvShowDetails;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class TvDetailsService {

    private static final int TMDB_MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 150L;

    private final RestTemplate restTemplate;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

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

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

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
