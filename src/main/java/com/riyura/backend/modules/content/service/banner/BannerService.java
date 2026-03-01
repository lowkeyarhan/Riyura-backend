package com.riyura.backend.modules.content.service.banner;

import com.riyura.backend.common.config.CacheStampedeGuard;
import com.riyura.backend.common.dto.tmdb.TmdbTrendingResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.util.GenreMapper;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.modules.content.dto.banner.BannerResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BannerService {

    private final TmdbClient tmdbClient;
    private final CacheStampedeGuard cacheStampedeGuard;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    @Value("${tmdb.image-base-url}")
    private String imageBaseUrl;

    // Fetches banner data for both movies and TV shows.
    public List<BannerResponse> getBannerData() {
        return cacheStampedeGuard.staleWhileRevalidate(
                "banners",
                Duration.ofHours(8),
                Duration.ofDays(1),
                () -> {
                    CompletableFuture<List<BannerResponse>> moviesTask = CompletableFuture
                            .supplyAsync(this::fetchTopMovies);
                    CompletableFuture<List<BannerResponse>> tvTask = CompletableFuture.supplyAsync(this::fetchTopTV);
                    List<BannerResponse> allItems = new ArrayList<>(moviesTask.orTimeout(8, TimeUnit.SECONDS).join());
                    allItems.addAll(tvTask.orTimeout(8, TimeUnit.SECONDS).join());
                    Collections.shuffle(allItems);
                    return allItems;
                });
    }

    // Fetches top trending movies from TMDb and maps them to BannerResponse DTOs
    private List<BannerResponse> fetchTopMovies() {
        return fetchAndMap(String.format("%s/trending/movie/week?api_key=%s", baseUrl, apiKey), MediaType.Movie);
    }

    // Fetches top trending TV shows from TMDb and maps them to BannerResponse DTOs
    private List<BannerResponse> fetchTopTV() {
        return fetchAndMap(String.format("%s/trending/tv/week?api_key=%s", baseUrl, apiKey), MediaType.TV);
    }

    // Helper method to fetch data from TMDb and map it to BannerResponse DTOs
    private List<BannerResponse> fetchAndMap(String url, MediaType type) {
        try {
            TmdbTrendingResponse response = tmdbClient.fetchWithRetry(url, TmdbTrendingResponse.class);
            if (response == null || response.getResults() == null)
                return Collections.emptyList();
            return response.getResults().stream()
                    .limit(3)
                    .map(item -> mapItemToBanner(item, type))
                    .toList();
        } catch (Exception e) {
            log.error("Error fetching banner data: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // Maps a TMDb item to a BannerResponse DTO, handling both movies and TV shows
    private BannerResponse mapItemToBanner(TmdbTrendingResponse.TmdbItem item, MediaType type) {
        BannerResponse model = new BannerResponse();
        model.setTmdbId(item.getId());

        if (type == MediaType.Movie) {
            model.setTitle(item.getTitle());
            model.setYear(TmdbUtils.extractYear(item.getReleaseDate()));
        } else {
            model.setTitle(item.getName());
            model.setYear(TmdbUtils.extractYear(item.getFirstAirDate()));
        }

        model.setOverview(item.getOverview() != null ? item.getOverview() : "");
        model.setMediaType(type);

        if (item.getBackdropPath() != null)
            model.setBackdropUrl(imageBaseUrl + item.getBackdropPath());

        List<Integer> ids = item.getGenreIds() != null ? item.getGenreIds() : Collections.emptyList();
        List<String> genreNames = ids.stream()
                .map(GenreMapper::getGenreName)
                .filter(Objects::nonNull)
                .toList();
        model.setGenres(genreNames);

        boolean isAdult = Boolean.TRUE.equals(item.getAdult());
        model.setAdult(isAdult);
        model.setMaturityRating(isAdult ? "A" : "U/A");
        return model;
    }
}