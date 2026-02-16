package com.riyura.backend.modules.tv.service;

import com.riyura.backend.common.dto.CastDetailsResponse;
import com.riyura.backend.modules.tv.dto.TvShowDetails;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class TvDetailsService {

    private final RestTemplate restTemplate = new RestTemplate();

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
                    .supplyAsync(() -> restTemplate.getForObject(detailsUrl, TvShowDetails.class));
            // Fetch casts
            CompletableFuture<CreditsResponse> creditsTask = CompletableFuture
                    .supplyAsync(() -> restTemplate.getForObject(creditsUrl, CreditsResponse.class));

            TvShowDetails details = detailsTask.join();
            CreditsResponse credits = creditsTask.join();

            // Set casts in details
            if (details != null) {
                details.setCasts(
                        credits != null && credits.getCast() != null ? credits.getCast() : Collections.emptyList());
            }

            return details;
        } catch (Exception e) {
            System.err.println("Error fetching TV details for ID " + id + ": " + e.getMessage());
            return null;
        }
    }

    // Inner class to map credits response
    @Data
    private static class CreditsResponse {
        private List<CastDetailsResponse> cast;
    }
}
