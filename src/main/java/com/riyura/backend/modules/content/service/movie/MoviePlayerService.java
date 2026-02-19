package com.riyura.backend.modules.content.service.movie;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.riyura.backend.common.dto.StreamUrlResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.service.StreamUrlService;
import com.riyura.backend.modules.content.dto.movie.MovieDetail;
import com.riyura.backend.modules.content.dto.movie.MoviePlayerResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MoviePlayerService {

    private static final int TMDB_MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 150L;

    private final RestTemplate restTemplate;
    private final StreamUrlService streamUrlService;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    public MoviePlayerResponse getMoviePlayer(String id) {
        String detailsUrl = String.format("%s/movie/%s?api_key=%s&language=en-US", baseUrl, id, apiKey);

        try {
            CompletableFuture<MovieDetail> detailsTask = CompletableFuture
                    .supplyAsync(() -> fetchWithRetry(detailsUrl, MovieDetail.class));

            CompletableFuture<List<StreamUrlResponse>> streamUrlsTask = CompletableFuture
                    .supplyAsync(() -> streamUrlService.fetchStreamUrls(MediaType.Movie));

            MovieDetail details = detailsTask.join();
            List<StreamUrlResponse> streamUrls = streamUrlsTask.join();

            if (details == null) {
                return null;
            }

            return mapToPlayerResponse(details, streamUrls);
        } catch (Exception e) {
            System.err.println("Error fetching movie player payload for ID " + id + ": " + rootMessage(e));
            return null;
        }
    }

    private MoviePlayerResponse mapToPlayerResponse(MovieDetail details, List<StreamUrlResponse> streamUrls) {
        MoviePlayerResponse response = new MoviePlayerResponse();
        response.setTmdbId(details.getTmdbId());
        response.setTitle(details.getTitle());
        response.setOverview(details.getOverview());
        response.setStreamUrls(streamUrls == null ? List.of() : streamUrls);

        List<String> genreNames = details.getGenres() == null ? List.of()
                : details.getGenres().stream()
                        .map(MovieDetail.Genre::getName)
                        .filter(Objects::nonNull)
                        .toList();
        response.setGenres(genreNames);

        response.setAnime(isAnime(details));

        return response;
    }

    private boolean isAnime(MovieDetail details) {
        return isJapaneseLanguage(details.getOriginalLanguage()) && hasAnimationGenre(details.getGenres());
    }

    private boolean isJapaneseLanguage(String originalLanguage) {
        return "ja".equalsIgnoreCase(originalLanguage);
    }

    private boolean hasAnimationGenre(List<MovieDetail.Genre> genres) {
        if (genres == null || genres.isEmpty()) {
            return false;
        }
        return genres.stream()
                .filter(Objects::nonNull)
                .anyMatch(genre -> "Animation".equals(genre.getName())
                        || (genre.getId() != null && genre.getId() == 16));
    }

    private <T> T fetchWithRetry(String url, Class<T> type) {
        ResourceAccessException lastResourceException = null;

        for (int attempt = 1; attempt <= TMDB_MAX_RETRIES; attempt++) {
            try {
                return restTemplate.getForObject(url, type);
            } catch (ResourceAccessException e) {
                lastResourceException = e;
                if (attempt == TMDB_MAX_RETRIES) {
                    throw e;
                }
                sleepBeforeRetry();
            } catch (RestClientException e) {
                throw e;
            }
        }

        throw lastResourceException;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
