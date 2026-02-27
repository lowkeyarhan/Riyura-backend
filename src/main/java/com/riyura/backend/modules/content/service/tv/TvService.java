package com.riyura.backend.modules.content.service.tv;

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
public class TvService {

    private final RestTemplate restTemplate;
    private final CacheStampedeGuard cacheStampedeGuard;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    @Value("${tmdb.image-base-url}")
    private String imageBaseUrl;

    public List<MediaGridResponse> getAiringToday(int limit) {
        return cacheStampedeGuard.xfetch(
                "tvAiringToday:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        String.format("%s/tv/airing_today?api_key=%s&language=en-US&page=1", baseUrl, apiKey),
                        limit));
    }

    public List<MediaGridResponse> getTrendingTv(int limit) {
        return cacheStampedeGuard.xfetch(
                "tvTrending:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        String.format("%s/trending/tv/week?api_key=%s&language=en-US", baseUrl, apiKey),
                        limit));
    }

    public List<MediaGridResponse> getPopularTv(int limit) {
        return cacheStampedeGuard.xfetch(
                "tvPopular:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        String.format("%s/tv/popular?api_key=%s&language=en-US&page=1", baseUrl, apiKey),
                        limit));
    }

    public List<MediaGridResponse> getOnTheAir(int limit) {
        return cacheStampedeGuard.xfetch(
                "tvOnTheAir:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        String.format("%s/tv/on_the_air?api_key=%s&language=en-US&page=1", baseUrl, apiKey),
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
            log.error("Error fetching TV data: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private MediaGridResponse mapToDTO(TmdbTrendingResponse.TmdbItem item) {
        MediaGridResponse dto = new MediaGridResponse();
        dto.setTmdbId(item.getId());
        dto.setTitle(item.getName());
        dto.setYear(TmdbUtils.extractYear(item.getFirstAirDate()));
        if (item.getPosterPath() != null)
            dto.setPosterUrl(imageBaseUrl + item.getPosterPath());
        dto.setMediaType(MediaType.TV);
        return dto;
    }
}