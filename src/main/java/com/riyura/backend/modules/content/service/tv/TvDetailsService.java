package com.riyura.backend.modules.content.service.tv;

import com.riyura.backend.common.dto.CastResponse;
import com.riyura.backend.common.dto.MediaGridResponse;
import com.riyura.backend.common.dto.TmdbTrendingResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.modules.content.dto.tv.TvShowDetails;

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
public class TvDetailsService {

    private static final int SIMILAR_LIMIT = 6;

    private final TmdbClient tmdbClient;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    @Value("${tmdb.image-base-url}")
    private String imageBaseUrl;

    // Fetches detailed information about a TV show, including its cast and whether
    // it's an anime
    public TvShowDetails getTvDetails(String id) {
        String detailsUrl = String.format("%s/tv/%s?api_key=%s&language=en-US", baseUrl, id, apiKey);
        String creditsUrl = String.format("%s/tv/%s/credits?api_key=%s&language=en-US", baseUrl, id, apiKey);

        try {
            CompletableFuture<TvShowDetails> detailsTask = CompletableFuture
                    .supplyAsync(() -> tmdbClient.fetchWithRetry(detailsUrl, TvShowDetails.class));
            CompletableFuture<CreditsResponse> creditsTask = CompletableFuture.supplyAsync(() -> {
                try {
                    return tmdbClient.fetchWithRetry(creditsUrl, CreditsResponse.class);
                } catch (Exception e) {
                    return null;
                }
            });

            TvShowDetails details = detailsTask.join();
            CreditsResponse credits = creditsTask.join();

            if (details != null) {
                details.setCasts(
                        credits != null && credits.getCast() != null ? credits.getCast() : Collections.emptyList());
                details.setAnime(TmdbUtils.isAnime(details.getOriginalLanguage(), details.getGenres()));
            }
            return details;
        } catch (Exception e) {
            System.err.println("Error fetching TV details for ID " + id + ": " + TmdbClient.rootMessage(e));
            return null;
        }
    }

    // Fetches similar TV shows based on a given TV show ID, sorted by rating and
    // limited to a certain number
    public List<MediaGridResponse> getSimilarTvShows(String id) {
        String similarUrl = String.format("%s/tv/%s/similar?api_key=%s&language=en-US&page=1", baseUrl, id, apiKey);

        try {
            TmdbTrendingResponse response = tmdbClient.fetchWithRetry(similarUrl, TmdbTrendingResponse.class);
            if (response == null || response.getResults() == null)
                return Collections.emptyList();

            return response.getResults().stream()
                    .sorted(Comparator.comparing(TmdbTrendingResponse.TmdbItem::getVoteAverage,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(SIMILAR_LIMIT)
                    .map(this::mapSimilarTvToDTO)
                    .toList();
        } catch (Exception e) {
            System.err.println("Error fetching similar TV shows for ID " + id + ": " + TmdbClient.rootMessage(e));
            return Collections.emptyList();
        }
    }

    // Maps a TMDb item to a MediaGridResponse DTO for similar TV shows
    private MediaGridResponse mapSimilarTvToDTO(TmdbTrendingResponse.TmdbItem item) {
        MediaGridResponse dto = new MediaGridResponse();
        dto.setTmdbId(item.getId());
        dto.setTitle(item.getName());
        dto.setYear(TmdbUtils.extractYear(item.getFirstAirDate()));
        dto.setMediaType(MediaType.TV);
        if (item.getPosterPath() != null && !item.getPosterPath().isEmpty()) {
            dto.setPosterUrl(imageBaseUrl + item.getPosterPath());
        }
        return dto;
    }

    // Fetches search results for a TV show by its ID, including both movies and TV
    // shows that match the ID as a filter
    @Data
    private static class CreditsResponse {
        private List<CastResponse> cast;
    }
}
