package com.riyura.backend.modules.content.service.movie;

import com.riyura.backend.common.cache.CacheStampedeGuard;
import com.riyura.backend.common.dto.media.MediaGridResponse;
import com.riyura.backend.common.dto.tmdb.TmdbTrendingResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.util.TmdbUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieService {

    private final RestTemplate restTemplate;
    private final CacheStampedeGuard cacheStampedeGuard;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    @Value("${tmdb.image-base-url}")
    private String imageBaseUrl;

    public List<MediaGridResponse> getNowPlayingMovies(int limit) {
        return cacheStampedeGuard.xfetch(
                "moviesNowPlaying:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        String.format("%s/movie/now_playing?api_key=%s&language=en-US&page=1", baseUrl, apiKey),
                        limit));
    }

    public List<MediaGridResponse> getTrendingMovies(int limit) {
        return cacheStampedeGuard.xfetch(
                "moviesTrending:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        String.format("%s/trending/movie/week?api_key=%s&language=en-US", baseUrl, apiKey),
                        limit));
    }

    public List<MediaGridResponse> getPopularMovies(int limit) {
        return cacheStampedeGuard.xfetch(
                "moviesPopular:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        String.format("%s/movie/popular?api_key=%s&language=en-US&page=1", baseUrl, apiKey),
                        limit));
    }

    public List<MediaGridResponse> getUpcomingMovies(int limit) {
        return cacheStampedeGuard.xfetch(
                "moviesUpcoming:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        String.format("%s/movie/upcoming?api_key=%s&language=en-US&page=1", baseUrl, apiKey),
                        limit));
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
            log.error("Error fetching movie data: {}", e.getMessage());
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