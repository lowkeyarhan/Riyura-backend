package com.riyura.backend.modules.content.service.movie;

import com.riyura.backend.common.dto.MediaGridResponse;
import com.riyura.backend.common.dto.TmdbTrendingResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.util.TmdbUtils;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final RestTemplate restTemplate;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    @Value("${tmdb.image-base-url}")
    private String imageBaseUrl;

    public List<MediaGridResponse> getNowPlayingMovies(int limit) {
        return fetchAndMap(String.format("%s/movie/now_playing?api_key=%s&language=en-US&page=1", baseUrl, apiKey),
                limit);
    }

    public List<MediaGridResponse> getTrendingMovies(int limit) {
        return fetchAndMap(String.format("%s/trending/movie/week?api_key=%s&language=en-US", baseUrl, apiKey), limit);
    }

    public List<MediaGridResponse> getPopularMovies(int limit) {
        return fetchAndMap(String.format("%s/movie/popular?api_key=%s&language=en-US&page=1", baseUrl, apiKey), limit);
    }

    public List<MediaGridResponse> getUpcomingMovies(int limit) {
        return fetchAndMap(String.format("%s/movie/upcoming?api_key=%s&language=en-US&page=1", baseUrl, apiKey), limit);
    }

    private List<MediaGridResponse> fetchAndMap(String url, int limit) {
        try {
            TmdbTrendingResponse response = restTemplate.getForObject(url, TmdbTrendingResponse.class);
            if (response == null || response.getResults() == null)
                return Collections.emptyList();

            return response.getResults().stream()
                    .filter(item -> item.getPosterPath() != null && !item.getPosterPath().isEmpty())
                    .limit(limit)
                    .map(this::mapToDTO)
                    .toList();
        } catch (Exception e) {
            System.err.println("Error fetching movie data: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private MediaGridResponse mapToDTO(TmdbTrendingResponse.TmdbItem item) {
        MediaGridResponse dto = new MediaGridResponse();
        dto.setTmdbId(item.getId());
        dto.setTitle(item.getTitle());
        dto.setYear(TmdbUtils.extractYear(item.getReleaseDate()));
        if (item.getPosterPath() != null)
            dto.setPosterUrl(imageBaseUrl + item.getPosterPath());
        dto.setMediaType(MediaType.Movie);
        return dto;
    }
}