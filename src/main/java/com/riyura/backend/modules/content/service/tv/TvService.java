package com.riyura.backend.modules.content.service.tv;

import com.riyura.backend.common.config.CacheStampedeGuard;
import com.riyura.backend.common.config.TmdbProperties;
import com.riyura.backend.common.dto.media.MediaGridResponse;
import com.riyura.backend.common.dto.tmdb.TmdbTrendingResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.common.service.TmdbUrlBuilder;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.modules.content.port.TvServicePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TvService implements TvServicePort {

    private final TmdbClient tmdbClient;
    private final CacheStampedeGuard cacheStampedeGuard;
    private final TmdbProperties tmdbProperties;
    private static final int MAX_PAGES = 5;

    @Override
    public List<MediaGridResponse> getAiringToday(int limit) {
        return cacheStampedeGuard.xfetch(
                "tvAiringToday:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        TmdbUrlBuilder.from(tmdbProperties)
                                .path("/tv/airing_today")
                                .param("language", "en-US")
                                .build(),
                        limit));
    }

    @Override
    public List<MediaGridResponse> getTrendingTv(int limit) {
        return cacheStampedeGuard.xfetch(
                "tvTrending:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        TmdbUrlBuilder.from(tmdbProperties)
                                .path("/trending/tv/week")
                                .param("language", "en-US")
                                .build(),
                        limit));
    }

    @Override
    public List<MediaGridResponse> getPopularTv(int limit) {
        return cacheStampedeGuard.xfetch(
                "tvPopular:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        TmdbUrlBuilder.from(tmdbProperties)
                                .path("/tv/popular")
                                .param("language", "en-US")
                                .build(),
                        limit));
    }

    @Override
    public List<MediaGridResponse> getOnTheAir(int limit) {
        return cacheStampedeGuard.xfetch(
                "tvOnTheAir:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        TmdbUrlBuilder.from(tmdbProperties)
                                .path("/tv/on_the_air")
                                .param("language", "en-US")
                                .build(),
                        limit));
    }

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
            dto.setPosterUrl(tmdbProperties.imageBaseUrl() + item.getPosterPath());
        dto.setMediaType(MediaType.TV);
        return dto;
    }
}