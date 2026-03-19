package com.riyura.backend.modules.content.service.movie;

import com.riyura.backend.common.config.CacheStampedeGuard;
import com.riyura.backend.common.dto.media.MediaGridResponse;
import com.riyura.backend.common.dto.tmdb.TmdbTrendingResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.modules.content.dto.global.CastResponse;
import com.riyura.backend.modules.content.dto.movie.MovieDetail;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieDetailService {

    private static final int SIMILAR_LIMIT = 6;

    private final TmdbClient tmdbClient;
    private final CacheStampedeGuard cacheStampedeGuard;
    private final Executor tmdbExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    @Value("${tmdb.image-base-url}")
    private String imageBaseUrl;

    // Fetches detailed information about a movie, including its cast and whether
    // it's an anime.
    public MovieDetail getMovieDetails(String id) {
        return cacheStampedeGuard.xfetch(
                "movieDetails:" + id, Duration.ofDays(7), 1.5,
                () -> {
                    // Build the URLs for the movie details and credits
                    String detailsUrl = String.format("%s/movie/%s?api_key=%s&language=en-US", baseUrl, id, apiKey);
                    String creditsUrl = String.format("%s/movie/%s/credits?api_key=%s&language=en-US",
                            baseUrl, id, apiKey);
                    try {
                        // Fetch the movie details and credits in parallel
                        CompletableFuture<MovieDetail> detailsTask = CompletableFuture
                                .supplyAsync(() -> tmdbClient.fetchWithRetry(detailsUrl, MovieDetail.class),
                                        tmdbExecutor);
                        // Fetch the credits in parallel
                        CompletableFuture<CreditsResponse> creditsTask = CompletableFuture.supplyAsync(() -> {
                            try {
                                return tmdbClient.fetchWithRetry(creditsUrl, CreditsResponse.class);
                            } catch (Exception e) {
                                return null;
                            }
                        }, tmdbExecutor);
                        // Join the tasks and map the results
                        MovieDetail details = detailsTask.orTimeout(8, TimeUnit.SECONDS).join();
                        CreditsResponse credits = creditsTask.orTimeout(8, TimeUnit.SECONDS).join();
                        if (details != null) {
                            // Set the casts
                            details.setCasts(credits != null && credits.getCast() != null
                                    ? credits.getCast()
                                    : Collections.emptyList());
                            // Set the anime flag
                            details.setAnime(TmdbUtils.isAnime(details.getOriginalLanguage(), details.getGenres()));
                            details.setMaturityRating(details.isAdult() ? "A" : "U/A");
                        }
                        return details;
                    } catch (Exception e) {
                        log.error("Error fetching movie details for ID {}: {}", id, TmdbClient.rootMessage(e));
                        return null;
                    }
                });
    }

    // Fetches similar movies based on a given movie ID, sorted by rating and
    // limited to a predefined number
    public List<MediaGridResponse> getSimilarMovies(String id) {
        return cacheStampedeGuard.xfetch(
                "movieSimilar:" + id, Duration.ofDays(7), 1.0,
                () -> {
                    // Build the URL for the similar movies
                    String similarUrl = String.format(
                            "%s/movie/%s/similar?api_key=%s&language=en-US&page=1", baseUrl, id, apiKey);
                    try {
                        // Fetch the similar movies
                        TmdbTrendingResponse response = tmdbClient.fetchWithRetry(similarUrl,
                                TmdbTrendingResponse.class);
                        if (response == null || response.getResults() == null)
                            return Collections.emptyList();
                        // Sort the movies by rating and map to MediaGridResponse
                        return response.getResults().stream()
                                .sorted(Comparator.comparing(TmdbTrendingResponse.TmdbItem::getVoteAverage,
                                        Comparator.nullsLast(Comparator.reverseOrder())))
                                .limit(SIMILAR_LIMIT)
                                .map(this::mapSimilarMovieToDTO)
                                .toList();
                    } catch (Exception e) {
                        log.error("Error fetching similar movies for ID {}: {}", id, TmdbClient.rootMessage(e));
                        return Collections.emptyList();
                    }
                });
    }

    // Maps a TMDb item to a MediaGridResponse DTO, specifically for similar movies
    private MediaGridResponse mapSimilarMovieToDTO(TmdbTrendingResponse.TmdbItem item) {
        // Map the TMDb item to a MediaGridResponse
        MediaGridResponse dto = new MediaGridResponse();
        dto.setTmdbId(item.getId());
        dto.setTitle(item.getTitle());
        dto.setYear(TmdbUtils.extractYear(item.getReleaseDate()));
        dto.setMediaType(MediaType.Movie);
        if (item.getPosterPath() != null && !item.getPosterPath().isEmpty()) {
            dto.setPosterUrl(imageBaseUrl + item.getPosterPath());
        }
        return dto;
    }

    // Inner class to represent the credits response from TMDb, containing a list of
    // cast members
    @Data
    private static class CreditsResponse {
        private List<CastResponse> cast;
    }
}
