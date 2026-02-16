/* */
package com.riyura.backend.modules.movie.service;

import com.riyura.backend.common.dto.CastDetailsResponse;
import com.riyura.backend.modules.movie.dto.MovieDetail;

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
public class MovieDetailService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    // Get Movie Details by ID
    public MovieDetail getMovieDetails(String id) {
        String detailsUrl = String.format("%s/movie/%s?api_key=%s&language=en-US", baseUrl, id, apiKey);
        String creditsUrl = String.format("%s/movie/%s/credits?api_key=%s&language=en-US", baseUrl, id, apiKey);

        try {
            // Fetch details
            CompletableFuture<MovieDetail> detailsTask = CompletableFuture
                    .supplyAsync(() -> restTemplate.getForObject(detailsUrl, MovieDetail.class));
            // Fetch casts
            CompletableFuture<CreditsResponse> creditsTask = CompletableFuture
                    .supplyAsync(() -> restTemplate.getForObject(creditsUrl, CreditsResponse.class));

            // Join results
            MovieDetail details = detailsTask.join();
            CreditsResponse credits = creditsTask.join();

            // Set casts in details
            if (details != null) {
                details.setCasts(
                        credits != null && credits.getCast() != null ? credits.getCast() : Collections.emptyList());
            }

            return details;
        } catch (Exception e) {
            System.err.println("Error fetching movie details for ID " + id + ": " + e.getMessage());
            return null;
        }
    }

    // Inner class to map credits response
    @Data
    private static class CreditsResponse {
        private List<CastDetailsResponse> cast;
    }
}
