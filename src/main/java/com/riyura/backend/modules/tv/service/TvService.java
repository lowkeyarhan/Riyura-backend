package com.riyura.backend.modules.tv.service;

import com.riyura.backend.common.dto.MediaGridResponse;
import com.riyura.backend.common.dto.TmdbTrendingDTO;
import com.riyura.backend.common.model.MediaType;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TvService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    @Value("${tmdb.image-base-url}")
    private String imageBaseUrl;

    // Get Airing Today TV Shows with a limit (e.g., top 10)
    public List<MediaGridResponse> getAiringToday(int limit) {
        String url = String.format("%s/tv/airing_today?api_key=%s&language=en-US&page=1", baseUrl, apiKey);
        return fetchAndMap(url, limit);
    }

    // Get Trending TV Shows with a limit (e.g., top 10)
    public List<MediaGridResponse> getTrendingTv(int limit) {
        String url = String.format("%s/trending/tv/week?api_key=%s&language=en-US", baseUrl, apiKey);
        return fetchAndMap(url, limit);
    }

    // Get Popular TV Shows with a limit (e.g., top 10)
    public List<MediaGridResponse> getPopularTv(int limit) {
        String url = String.format("%s/tv/popular?api_key=%s&language=en-US&page=1", baseUrl, apiKey);
        return fetchAndMap(url, limit);
    }

    // Get releasing soon TV Shows with a limit (e.g., top 10)
    public List<MediaGridResponse> getOnTheAir(int limit) {
        String url = String.format("%s/tv/on_the_air?api_key=%s&language=en-US&page=1", baseUrl, apiKey);
        return fetchAndMap(url, limit);
    }

    // Helper method to fetch data from TMDB and map it to our DTO
    private List<MediaGridResponse> fetchAndMap(String url, int limit) {
        try {
            // Reusing TmdbTrendingDTO
            TmdbTrendingDTO response = restTemplate.getForObject(url, TmdbTrendingDTO.class);

            // Handle null response or null results
            if (response == null || response.getResults() == null) {
                return Collections.emptyList();
            }

            // Filter out items without poster and limit the results, then map to DTO
            return response.getResults().stream()
                    .filter(item -> item.getPosterPath() != null && !item.getPosterPath().isEmpty())
                    .limit(limit)
                    .map(this::mapToDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error fetching TV data: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // Map TMDB item to our MediaGridItemDTO
    private MediaGridResponse mapToDTO(TmdbTrendingDTO.TmdbItem item) {
        MediaGridResponse dto = new MediaGridResponse();

        dto.setTmdbId(item.getId());

        // TV Shows use 'name' instead of 'title'
        dto.setTitle(item.getName());

        // Map Year from 'firstAirDate'
        dto.setYear(extractYear(item.getFirstAirDate()));

        if (item.getPosterPath() != null) {
            dto.setPosterUrl(imageBaseUrl + item.getPosterPath());
        }

        dto.setMediaType(MediaType.TV);

        return dto;
    }

    // Helper method to extract year from a date string (e.g., "2021-09-17")
    private String extractYear(String dateString) {
        if (dateString == null || dateString.isEmpty())
            return null;
        try {
            return String.valueOf(LocalDate.parse(dateString).getYear());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}