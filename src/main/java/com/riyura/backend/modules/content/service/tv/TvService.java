package com.riyura.backend.modules.content.service.tv;

import com.riyura.backend.common.config.CacheStampedeGuard;
import com.riyura.backend.common.dto.media.MediaGridResponse;
import com.riyura.backend.common.dto.tmdb.TmdbTrendingResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.common.util.TmdbUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TvService {

    private final TmdbClient tmdbClient;
    private final CacheStampedeGuard cacheStampedeGuard;
    private static final int MAX_PAGES = 5;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    @Value("${tmdb.image-base-url}")
    private String imageBaseUrl;

    // Fetch the "Airing Today" TV shows, applying filters and caching results to
    // prevent stampedes
    public List<MediaGridResponse> getAiringToday(int limit) {
        return cacheStampedeGuard.xfetch(
                "tvAiringToday:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        String.format("%s/tv/airing_today?api_key=%s&language=en-US", baseUrl, apiKey),
                        limit));
    }

    // Fetch the "Trending This Week" TV shows, applying filters and caching results
    // to prevent stampedes
    public List<MediaGridResponse> getTrendingTv(int limit) {
        return cacheStampedeGuard.xfetch(
                "tvTrending:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        String.format("%s/trending/tv/week?api_key=%s&language=en-US", baseUrl, apiKey),
                        limit));
    }

    // Fetch the "Top Rated" TV shows, applying filters and caching results to
    // prevent stampedes
    public List<MediaGridResponse> getPopularTv(int limit) {
        return cacheStampedeGuard.xfetch(
                "tvPopular:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        String.format("%s/tv/popular?api_key=%s&language=en-US", baseUrl, apiKey),
                        limit));
    }

    // Fetch the "On The Air" TV shows, applying filters and caching results to
    // prevent stampedes
    public List<MediaGridResponse> getOnTheAir(int limit) {
        return cacheStampedeGuard.xfetch(
                "tvOnTheAir:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        String.format("%s/tv/on_the_air?api_key=%s&language=en-US", baseUrl, apiKey),
                        limit));
    }

    // Helper method to fetch TV data from TMDB, apply filters, and map results to
    // DTOs
    private List<MediaGridResponse> fetchAndMap(String baseEndpointUrl, int limit) {
        List<TmdbTrendingResponse.TmdbItem> collected = new ArrayList<>();
        int page = 1;

        while (collected.size() < limit && page <= MAX_PAGES) {
            String url = baseEndpointUrl + "&page=" + page;
            try {
                TmdbTrendingResponse response = tmdbClient.fetchWithRetry(url, TmdbTrendingResponse.class);
                if (response == null || response.getResults() == null || response.getResults().isEmpty())
                    break;

                response.getResults().stream()
                        .filter(item -> item.getPosterPath() != null && !item.getPosterPath().isEmpty())
                        .filter(item -> !TmdbUtils.isAnimeByIds(item.getOriginalLanguage(), item.getGenreIds()))
                        .filter(item -> !TmdbUtils.isTalkShow(item.getGenreIds()))
                        .filter(item -> !TmdbUtils.isSoapOpera(item.getGenreIds()))
                        .forEach(collected::add);

                // No more pages available from TMDB
                if (page >= response.getTotalPages())
                    break;

                page++;
            } catch (Exception e) {
                log.error("Error fetching TV data (page {}): {}", page, e.getMessage());
                break;
            }
        }

        return collected.stream()
                .limit(limit)
                .map(this::mapToDTO)
                .toList();
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