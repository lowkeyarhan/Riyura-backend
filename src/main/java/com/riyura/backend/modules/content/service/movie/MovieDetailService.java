package com.riyura.backend.modules.content.service.movie;

import com.riyura.backend.common.dto.cast.CastResponse;
import com.riyura.backend.common.dto.media.MediaGridResponse;
import com.riyura.backend.common.dto.tmdb.TmdbTrendingResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.modules.content.dto.movie.MovieDetail;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class MovieDetailService {

    private static final int SIMILAR_LIMIT = 6;

    private final TmdbClient tmdbClient;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    @Value("${tmdb.image-base-url}")
    private String imageBaseUrl;

    // Fetches detailed information about a movie, including its cast and whether
    // it's an anime
    public MovieDetail getMovieDetails(String id) {
        String detailsUrl = String.format("%s/movie/%s?api_key=%s&language=en-US", baseUrl, id, apiKey);
        String creditsUrl = String.format("%s/movie/%s/credits?api_key=%s&language=en-US", baseUrl, id, apiKey);

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

            MovieDetail details = detailsTask.join();
            CreditsResponse credits = creditsTask.join();

            if (details != null) {
                details.setCasts(
                        credits != null && credits.getCast() != null ? credits.getCast() : Collections.emptyList());
                details.setAnime(TmdbUtils.isAnime(details.getOriginalLanguage(), details.getGenres()));
            }
            return details;
        } catch (Exception e) {
            System.err.println("Error fetching movie details for ID " + id + ": " + TmdbClient.rootMessage(e));
            return null;
        }
    }

    // Fetches similar movies based on a given movie ID, sorted by rating and
    // limited to a predefined number
    public List<MediaGridResponse> getSimilarMovies(String id) {
        String similarUrl = String.format("%s/movie/%s/similar?api_key=%s&language=en-US&page=1", baseUrl, id, apiKey);

        try {
            TmdbTrendingResponse response = tmdbClient.fetchWithRetry(similarUrl, TmdbTrendingResponse.class);
            if (response == null || response.getResults() == null)
                return Collections.emptyList();

            return response.getResults().stream()
                    .sorted(Comparator.comparing(TmdbTrendingResponse.TmdbItem::getVoteAverage,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(SIMILAR_LIMIT)
                    .map(this::mapSimilarMovieToDTO)
                    .toList();
        } catch (Exception e) {
            System.err.println("Error fetching similar movies for ID " + id + ": " + TmdbClient.rootMessage(e));
            return Collections.emptyList();
        }
    }

    // Maps a TMDb item to a MediaGridResponse DTO, specifically for similar movies
    private MediaGridResponse mapSimilarMovieToDTO(TmdbTrendingResponse.TmdbItem item) {
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
