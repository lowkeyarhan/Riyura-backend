package com.riyura.backend.modules.content.service.tv;

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
import com.riyura.backend.modules.content.dto.tv.TvPlayerResponse;
import com.riyura.backend.modules.content.dto.tv.TvShowDetails;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TvPlayerService {

    private static final int TMDB_MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 150L;

    private final RestTemplate restTemplate;
    private final StreamUrlService streamUrlService;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    // Get TV Player Info by ID
    public TvPlayerResponse getTvPlayer(String id) {
        String detailsUrl = String.format("%s/tv/%s?api_key=%s&language=en-US", baseUrl, id, apiKey);

        try {
            // Fetch details
            CompletableFuture<TvShowDetails> detailsTask = CompletableFuture
                    .supplyAsync(() -> fetchWithRetry(detailsUrl, TvShowDetails.class));

            // Fetch stream URLs
            CompletableFuture<List<StreamUrlResponse>> streamUrlsTask = CompletableFuture
                    .supplyAsync(() -> streamUrlService.fetchStreamUrls(MediaType.TV));

            // Wait for both tasks to complete
            TvShowDetails details = detailsTask.join();
            List<StreamUrlResponse> streamUrls = streamUrlsTask.join();

            // If details is null, return null (e.g., TV show not found)
            if (details == null) {
                return null;
            }

            return mapToPlayerResponse(details, streamUrls);
        } catch (Exception e) {
            System.err.println("Error fetching TV player payload for ID " + id + ": " + rootMessage(e));
            return null;
        }
    }

    // Helper method to fetch data with retry logic for ResourceAccessException
    private TvPlayerResponse mapToPlayerResponse(TvShowDetails details, List<StreamUrlResponse> streamUrls) {
        TvPlayerResponse response = new TvPlayerResponse();
        response.setTmdbId(details.getTmdbId());
        response.setTitle(details.getTitle());
        response.setOverview(details.getOverview());
        response.setStreamUrls(streamUrls == null ? List.of() : streamUrls);

        // Extract genre names, filtering out nulls
        List<String> genreNames = details.getGenres() == null ? List.of()
                : details.getGenres().stream()
                        .map(TvShowDetails.Genre::getName)
                        .filter(Objects::nonNull)
                        .toList();
        response.setGenres(genreNames);

        // Filter seasons to exclude nulls and season_number == 0 (specials)
        List<TvShowDetails.Season> filteredSeasons = details.getSeasons() == null ? List.of()
                : details.getSeasons().stream()
                        .filter(Objects::nonNull)
                        .filter(season -> season.getSeasonNumber() == null || season.getSeasonNumber() != 0)
                        .toList();
        response.setSeasons(filteredSeasons);

        return response;
    }

    // Helper method to fetch data with retry logic for ResourceAccessException
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

    // Helper method to sleep before retrying
    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // Helper method to extract the root cause message from an exception
    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
