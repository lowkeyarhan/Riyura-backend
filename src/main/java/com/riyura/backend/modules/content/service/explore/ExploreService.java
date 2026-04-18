package com.riyura.backend.modules.content.service.explore;

import com.riyura.backend.common.config.CacheStampedeGuard;
import com.riyura.backend.common.config.TmdbProperties;
import com.riyura.backend.common.dto.tmdb.TmdbDiscoverResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.common.service.TmdbUrlBuilder;
import com.riyura.backend.common.util.GenreMapper;
import com.riyura.backend.common.util.LanguageMapper;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.modules.content.dto.explore.ExploreResponse;
import com.riyura.backend.modules.content.port.ExploreServicePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExploreService implements ExploreServicePort {

    private static final int ITEMS_PER_TYPE = 9;

    private final TmdbClient tmdbClient;
    private final CacheStampedeGuard cacheStampedeGuard;
    private final TmdbProperties tmdbProperties;

    @Override
    public List<ExploreResponse> getExplorePage(int page, String genreNames, String language) {
        String cacheKey = String.format("explore:%d:%s:%s",
                page,
                Objects.toString(genreNames, "").toLowerCase(),
                Objects.toString(language, "").toLowerCase());

        return cacheStampedeGuard.staleWhileRevalidate(
                cacheKey,
                Duration.ofHours(12),
                Duration.ofDays(1),
                () -> {
                    String isoLanguage = LanguageMapper.toIsoCode(language);
                    String movieGenreIds = GenreMapper.toMovieGenreIds(genreNames);
                    String tvGenreIds = GenreMapper.toTvGenreIds(genreNames);

                    String movieUrl = buildUrl("movie", page, movieGenreIds, isoLanguage);
                    String tvUrl = buildUrl("tv", page, tvGenreIds, isoLanguage);

                    CompletableFuture<List<ExploreResponse>> moviesFuture = CompletableFuture
                            .supplyAsync(() -> fetchAndMap(movieUrl, MediaType.Movie));
                    CompletableFuture<List<ExploreResponse>> tvFuture = CompletableFuture
                            .supplyAsync(() -> fetchAndMap(tvUrl, MediaType.TV));

                    List<ExploreResponse> combined = new ArrayList<>(
                            moviesFuture.orTimeout(8, TimeUnit.SECONDS).join());
                    combined.addAll(tvFuture.orTimeout(8, TimeUnit.SECONDS).join());
                    return combined;
                });
    }

    private String buildUrl(String mediaKind, int page, String genreIds, String isoLanguage) {
        return TmdbUrlBuilder.from(tmdbProperties)
                .path("/discover/" + mediaKind)
                .param("sort_by", "popularity.desc")
                .param("page", page)
                .param("with_genres", genreIds)
                .param("with_original_language", isoLanguage)
                .build();
    }

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

    private ExploreResponse mapToDto(TmdbDiscoverResponse.TmdbDiscoverItem item, MediaType mediaType) {
        ExploreResponse dto = new ExploreResponse();
        dto.setTmdbId(item.getId());
        dto.setMediaType(mediaType);
        dto.setOriginalLanguage(item.getOriginalLanguage());
        dto.setRating(item.getVoteAverage());
        dto.setDescription(item.getOverview());
        dto.setPosterPath(tmdbProperties.imageBaseUrl() + item.getPosterPath());

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
