package com.riyura.backend.modules.content.service.explore;

import com.riyura.backend.common.config.CacheStampedeGuard;
import com.riyura.backend.common.dto.tmdb.TmdbDiscoverResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.common.util.GenreMapper;
import com.riyura.backend.common.util.LanguageMapper;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.modules.content.dto.explore.ExploreResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExploreService {

    private static final int ITEMS_PER_TYPE = 9;

    private final TmdbClient tmdbClient;
    private final CacheStampedeGuard cacheStampedeGuard;
    private final Executor tmdbExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    @Value("${tmdb.image-base-url}")
    private String imageBaseUrl;

    // Fetch explore page with mixed movies and TV shows.
    public List<ExploreResponse> getExplorePage(int page, String genreNames, String language) {
        String cacheKey = String.format("explore:%d:%s:%s",
                page,
                Objects.toString(genreNames, ""),
                Objects.toString(language, ""));

        // Fetch the explore page from the cache
        return cacheStampedeGuard.staleWhileRevalidate(
                cacheKey,
                Duration.ofHours(12), // fresh window
                Duration.ofDays(1), // hard window
                () -> {
                    // Convert the language to ISO code
                    String isoLanguage = LanguageMapper.toIsoCode(language);
                    String movieGenreIds = GenreMapper.toMovieGenreIds(genreNames);
                    String tvGenreIds = GenreMapper.toTvGenreIds(genreNames);

                    // Build the URLs for the movies and TV shows
                    String movieUrl = buildUrl("movie", page, movieGenreIds, isoLanguage);
                    String tvUrl = buildUrl("tv", page, tvGenreIds, isoLanguage);

                    // Fetch the movies and TV shows in parallel
                    CompletableFuture<List<ExploreResponse>> moviesFuture = CompletableFuture
                            .supplyAsync(() -> fetchAndMap(movieUrl, MediaType.Movie), tmdbExecutor);
                    CompletableFuture<List<ExploreResponse>> tvFuture = CompletableFuture
                            .supplyAsync(() -> fetchAndMap(tvUrl, MediaType.TV), tmdbExecutor);

                    List<ExploreResponse> combined = new ArrayList<>(
                            moviesFuture.orTimeout(8, TimeUnit.SECONDS).join());
                    combined.addAll(tvFuture.orTimeout(8, TimeUnit.SECONDS).join());
                    return combined;
                });
    }

    // Build URL for TMDB API
    private String buildUrl(String mediaKind, int page, String genreIds, String isoLanguage) {
        StringBuilder url = new StringBuilder(
                String.format("%s/discover/%s?api_key=%s&sort_by=popularity.desc&page=%d",
                        baseUrl, mediaKind, apiKey, page));

        if (genreIds != null && !genreIds.isBlank()) {
            url.append("&with_genres=").append(genreIds);
        }
        if (isoLanguage != null && !isoLanguage.isBlank()) {
            url.append("&with_original_language=").append(isoLanguage);
        }

        return url.toString();
    }

    // Fetch and map TMDB response to ExploreDto
    private List<ExploreResponse> fetchAndMap(String url, MediaType mediaType) {
        try {
            TmdbDiscoverResponse response = tmdbClient.fetchWithRetry(url, TmdbDiscoverResponse.class);
            if (response == null || response.getResults() == null)
                return Collections.emptyList();

            return response.getResults().stream()
                    .filter(item -> item.getPosterPath() != null && !item.getPosterPath().isEmpty())
                    .limit(ITEMS_PER_TYPE)
                    .map(item -> mapToDto(item, mediaType))
                    .toList();
        } catch (Exception e) {
            log.error("Error fetching explore data ({}): {}", mediaType, e.getMessage());
            return Collections.emptyList();
        }
    }

    // Map TMDB response to ExploreDto
    private ExploreResponse mapToDto(TmdbDiscoverResponse.TmdbDiscoverItem item, MediaType mediaType) {
        ExploreResponse dto = new ExploreResponse();
        dto.setTmdbId(item.getId());
        dto.setMediaType(mediaType);
        dto.setOriginalLanguage(item.getOriginalLanguage());
        dto.setRating(item.getVoteAverage());
        dto.setDescription(item.getOverview());
        dto.setPosterPath(imageBaseUrl + item.getPosterPath());

        if (mediaType == MediaType.Movie) {
            dto.setTitle(item.getTitle());
            dto.setReleaseYear(TmdbUtils.extractYear(item.getReleaseDate()));
        } else {
            dto.setTitle(item.getName());
            dto.setReleaseYear(TmdbUtils.extractYear(item.getFirstAirDate()));
        }

        return dto;
    }
}
