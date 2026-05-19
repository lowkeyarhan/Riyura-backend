package com.riyura.backend.modules.content.service.banner;

import com.riyura.backend.common.config.CacheStampedeGuard;
import com.riyura.backend.common.config.TmdbProperties;
import com.riyura.backend.common.dto.tmdb.TmdbTrendingResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.util.GenreLike;
import com.riyura.backend.common.util.GenreMapper;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.common.service.TmdbUrlBuilder;
import com.riyura.backend.modules.content.dto.banner.BannerResponse;
import com.riyura.backend.modules.content.dto.global.ContentRatingsResponse;
import com.riyura.backend.modules.content.dto.global.ReleaseDatesResponse;
import com.riyura.backend.modules.content.interfaces.BannerServicePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BannerService implements BannerServicePort {

    private final TmdbClient tmdbClient;
    private final CacheStampedeGuard cacheStampedeGuard;
    private final TmdbProperties tmdbProperties;

    @Override
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

    private List<BannerResponse> fetchTopMovies() {
        String url = TmdbUrlBuilder.from(tmdbProperties).path("/trending/movie/week").build();
        return fetchAndMap(url, MediaType.Movie);
    }

    private List<BannerResponse> fetchTopTV() {
        String url = TmdbUrlBuilder.from(tmdbProperties).path("/trending/tv/week").build();
        return fetchAndMap(url, MediaType.TV);
    }

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
            model.setBackdropUrl(tmdbProperties.imageBaseUrl() + item.getBackdropPath());

        List<Integer> ids = item.getGenreIds() != null ? item.getGenreIds() : Collections.emptyList();
        List<String> genreNames = ids.stream()
                .map(GenreMapper::getGenreName)
                .filter(Objects::nonNull)
                .toList();
        model.setGenres(genreNames);

        List<BannerGenre> genres = genreNames.stream()
                .map(BannerGenre::new)
                .toList();

        boolean isAdult = Boolean.TRUE.equals(item.getAdult());
        model.setAdult(isAdult);
        model.setMaturityRating(resolveMaturityRating(item, type, genres));
        return model;
    }

    private String resolveMaturityRating(TmdbTrendingResponse.TmdbItem item, MediaType type,
            List<? extends GenreLike> genres) {
        String overview = item.getOverview();
        if (type == MediaType.Movie) {
            ReleaseDatesResponse releaseDates = fetchMovieReleaseDates(item.getId());
            return TmdbUtils.getMovieMaturityRating(releaseDates, genres, overview);
        }
        ContentRatingsResponse contentRatings = fetchTvContentRatings(item.getId());
        return TmdbUtils.getTvMaturityRating(contentRatings, genres, overview);
    }

    private ReleaseDatesResponse fetchMovieReleaseDates(Long id) {
        if (id == null) {
            return null;
        }
        String url = TmdbUrlBuilder.from(tmdbProperties)
                .path("/movie/" + id + "/release_dates")
                .build();
        try {
            return tmdbClient.fetchWithRetry(url, ReleaseDatesResponse.class);
        } catch (Exception e) {
            log.debug("Failed to fetch movie release dates for {}: {}", id, e.getMessage());
            return null;
        }
    }

    private ContentRatingsResponse fetchTvContentRatings(Long id) {
        if (id == null) {
            return null;
        }
        String url = TmdbUrlBuilder.from(tmdbProperties)
                .path("/tv/" + id + "/content_ratings")
                .build();
        try {
            return tmdbClient.fetchWithRetry(url, ContentRatingsResponse.class);
        } catch (Exception e) {
            log.debug("Failed to fetch TV content ratings for {}: {}", id, e.getMessage());
            return null;
        }
    }

    private static class BannerGenre implements GenreLike {
        private final String name;

        private BannerGenre(String name) {
            this.name = name;
        }

        @Override
        public Long getId() {
            return null;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}