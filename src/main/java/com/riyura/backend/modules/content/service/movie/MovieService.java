package com.riyura.backend.modules.content.service.movie;

import com.riyura.backend.common.config.CacheStampedeGuard;
import com.riyura.backend.common.config.TmdbProperties;
import com.riyura.backend.common.dto.media.MediaGridResponse;
import com.riyura.backend.common.dto.tmdb.TmdbTrendingResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.common.service.TmdbUrlBuilder;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.modules.content.port.MovieServicePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieService implements MovieServicePort {

    private final TmdbClient tmdbClient;
    private final CacheStampedeGuard cacheStampedeGuard;
    private final TmdbProperties tmdbProperties;

    @Override
    public List<MediaGridResponse> getNowPlayingMovies(int limit) {
        return cacheStampedeGuard.xfetch(
                "moviesNowPlaying:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        TmdbUrlBuilder.from(tmdbProperties)
                                .path("/movie/now_playing")
                                .param("language", "en-US")
                                .param("page", 1)
                                .build(),
                        limit));
    }

    @Override
    public List<MediaGridResponse> getTrendingMovies(int limit) {
        return cacheStampedeGuard.xfetch(
                "moviesTrending:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        TmdbUrlBuilder.from(tmdbProperties)
                                .path("/trending/movie/week")
                                .param("language", "en-US")
                                .build(),
                        limit));
    }

    @Override
    public List<MediaGridResponse> getPopularMovies(int limit) {
        return cacheStampedeGuard.xfetch(
                "moviesPopular:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        TmdbUrlBuilder.from(tmdbProperties)
                                .path("/movie/popular")
                                .param("language", "en-US")
                                .param("page", 1)
                                .build(),
                        limit));
    }

    @Override
    public List<MediaGridResponse> getUpcomingMovies(int limit) {
        return cacheStampedeGuard.xfetch(
                "moviesUpcoming:" + limit, Duration.ofDays(1), 1.0,
                () -> fetchAndMap(
                        TmdbUrlBuilder.from(tmdbProperties)
                                .path("/movie/upcoming")
                                .param("language", "en-US")
                                .param("page", 1)
                                .build(),
                        limit));
    }

    private List<MediaGridResponse> fetchAndMap(String url, int limit) {
        try {
            TmdbTrendingResponse response = tmdbClient.fetchWithRetry(url, TmdbTrendingResponse.class);
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
            dto.setPosterUrl(tmdbProperties.imageBaseUrl() + item.getPosterPath());
        dto.setMediaType(MediaType.Movie);
        return dto;
    }
}