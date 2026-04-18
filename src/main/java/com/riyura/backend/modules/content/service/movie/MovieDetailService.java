package com.riyura.backend.modules.content.service.movie;

import com.riyura.backend.common.config.CacheStampedeGuard;
import com.riyura.backend.common.config.TmdbProperties;
import com.riyura.backend.common.dto.media.MediaGridResponse;
import com.riyura.backend.common.dto.tmdb.TmdbTrendingResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.common.service.TmdbUrlBuilder;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.modules.content.dto.global.CastResponse;
import com.riyura.backend.modules.content.dto.movie.MovieDetail;
import com.riyura.backend.modules.content.port.MovieDetailServicePort;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieDetailService implements MovieDetailServicePort {

    private static final int SIMILAR_LIMIT = 6;

    private final TmdbClient tmdbClient;
    private final CacheStampedeGuard cacheStampedeGuard;
    private final TmdbProperties tmdbProperties;

    @Override
    public MovieDetail getMovieDetails(String id) {
        return cacheStampedeGuard.xfetch(
                "movieDetails:" + id, Duration.ofDays(7), 1.5,
                () -> {
                    String detailsUrl = TmdbUrlBuilder.from(tmdbProperties)
                            .path("/movie/" + id)
                            .param("language", "en-US")
                            .build();
                    String creditsUrl = TmdbUrlBuilder.from(tmdbProperties)
                            .path("/movie/" + id + "/credits")
                            .param("language", "en-US")
                            .build();
                    try {
                        CompletableFuture<MovieDetail> detailsTask = CompletableFuture
                                .supplyAsync(() -> tmdbClient.fetchWithRetry(detailsUrl, MovieDetail.class));
                        CompletableFuture<CreditsResponse> creditsTask = CompletableFuture.supplyAsync(() -> {
                            try {
                                return tmdbClient.fetchWithRetry(creditsUrl, CreditsResponse.class);
                            } catch (Exception e) {
                                return null;
                            }
                        });

                        MovieDetail details = detailsTask.orTimeout(8, TimeUnit.SECONDS).join();
                        CreditsResponse credits = creditsTask.orTimeout(8, TimeUnit.SECONDS).join();
                        if (details != null) {
                            details.setCasts(credits != null && credits.getCast() != null
                                    ? credits.getCast()
                                    : Collections.emptyList());
                            details.setAnime(TmdbUtils.isAnime(details.getOriginalLanguage(), details.getGenres()));
                            details.setMaturityRating(details.isAdult() ? "A" : "U/A");
                        }
                        return details;
                    } catch (Exception e) {
                        log.error("Error fetching movie details for ID {}: {}", id, e.getMessage());
                        return null;
                    }
                });
    }

    @Override
    public List<MediaGridResponse> getSimilarMovies(String id) {
        return cacheStampedeGuard.xfetch(
                "movieSimilar:" + id, Duration.ofDays(7), 1.0,
                () -> {
                    String similarUrl = TmdbUrlBuilder.from(tmdbProperties)
                            .path("/movie/" + id + "/similar")
                            .param("language", "en-US")
                            .param("page", 1)
                            .build();
                    try {
                        TmdbTrendingResponse response = tmdbClient.fetchWithRetry(similarUrl,
                                TmdbTrendingResponse.class);
                        if (response == null || response.getResults() == null)
                            return Collections.emptyList();

                        return response.getResults().stream()
                                .sorted(Comparator.comparing(TmdbTrendingResponse.TmdbItem::getVoteAverage,
                                        Comparator.nullsLast(Comparator.reverseOrder())))
                                .limit(SIMILAR_LIMIT)
                                .map(this::mapSimilarMovieToDTO)
                                .toList();
                    } catch (Exception e) {
                        log.error("Error fetching similar movies for ID {}: {}", id, e.getMessage());
                        return Collections.emptyList();
                    }
                });
    }

    private MediaGridResponse mapSimilarMovieToDTO(TmdbTrendingResponse.TmdbItem item) {
        MediaGridResponse dto = new MediaGridResponse();
        dto.setTmdbId(item.getId());
        dto.setTitle(item.getTitle());
        dto.setYear(TmdbUtils.extractYear(item.getReleaseDate()));
        dto.setMediaType(MediaType.Movie);
        if (item.getPosterPath() != null && !item.getPosterPath().isEmpty()) {
            dto.setPosterUrl(tmdbProperties.imageBaseUrl() + item.getPosterPath());
        }
        return dto;
    }

    @Data
    private static class CreditsResponse {
        private List<CastResponse> cast;
    }
}
