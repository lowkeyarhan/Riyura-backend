package com.riyura.backend.modules.content.service.anime;

import com.riyura.backend.common.config.CacheStampedeGuard;
import com.riyura.backend.common.config.TmdbProperties;
import com.riyura.backend.common.dto.media.MediaGridResponse;
import com.riyura.backend.common.dto.tmdb.TmdbTrendingResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.common.service.TmdbUrlBuilder;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.modules.content.port.AnimeServicePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnimeService implements AnimeServicePort {

    private final TmdbClient tmdbClient;
    private final CacheStampedeGuard cacheStampedeGuard;
    private final TmdbProperties tmdbProperties;

    @Override
    public List<MediaGridResponse> getTrendingAnime(int limit) {
        return cacheStampedeGuard.xfetch(
                "animeTrending:" + limit, Duration.ofDays(1), 1.0,
                () -> {
                    CompletableFuture<List<AnimeHelper>> tvTask = CompletableFuture.supplyAsync(this::fetchAnimeTv);
                    CompletableFuture<List<AnimeHelper>> movieTask = CompletableFuture
                            .supplyAsync(this::fetchAnimeMovies);

                    List<AnimeHelper> allAnime = new ArrayList<>(tvTask.orTimeout(8, TimeUnit.SECONDS).join());
                    allAnime.addAll(movieTask.orTimeout(8, TimeUnit.SECONDS).join());

                    return allAnime.stream()
                            .filter(item -> item.tmdbItem().getVoteAverage() != null)
                            .sorted(Comparator.comparingDouble(
                                    (AnimeHelper h) -> h.tmdbItem().getVoteAverage()).reversed())
                            .limit(limit)
                            .map(this::mapToDTO)
                            .toList();
                });
    }

    private List<AnimeHelper> fetchAnimeTv() {
        String url = TmdbUrlBuilder.from(tmdbProperties)
                .path("/discover/tv")
                .param("sort_by", "popularity.desc")
                .param("vote_count.gte", "50")
                .param("with_genres", "16")
                .param("with_original_language", "ja")
                .param("page", 1)
                .build();
        return fetchAndWrap(url, MediaType.TV);
    }

    private List<AnimeHelper> fetchAnimeMovies() {
        String url = TmdbUrlBuilder.from(tmdbProperties)
                .path("/discover/movie")
                .param("sort_by", "popularity.desc")
                .param("vote_count.gte", "50")
                .param("with_genres", "16")
                .param("with_original_language", "ja")
                .param("page", 1)
                .build();
        return fetchAndWrap(url, MediaType.Movie);
    }

    private List<AnimeHelper> fetchAndWrap(String url, MediaType type) {
        try {
            TmdbTrendingResponse response = tmdbClient.fetchWithRetry(url, TmdbTrendingResponse.class);
            if (response == null || response.getResults() == null)
                return Collections.emptyList();
            return response.getResults().stream()
                    .filter(item -> item.getPosterPath() != null && !item.getPosterPath().isEmpty())
                    .map(item -> new AnimeHelper(item, type))
                    .toList();
        } catch (Exception e) {
            log.error("Error fetching anime ({}): {}", type, e.getMessage());
            return Collections.emptyList();
        }
    }

    private MediaGridResponse mapToDTO(AnimeHelper helper) {
        TmdbTrendingResponse.TmdbItem item = helper.tmdbItem;
        MediaGridResponse dto = new MediaGridResponse();
        dto.setTmdbId(item.getId());

        if (helper.type == MediaType.Movie) {
            dto.setTitle(item.getTitle());
            dto.setYear(TmdbUtils.extractYear(item.getReleaseDate()));
        } else {
            dto.setTitle(item.getName());
            dto.setYear(TmdbUtils.extractYear(item.getFirstAirDate()));
        }

        if (item.getPosterPath() != null)
            dto.setPosterUrl(tmdbProperties.imageBaseUrl() + item.getPosterPath());
        dto.setMediaType(helper.type);
        return dto;
    }

    private record AnimeHelper(TmdbTrendingResponse.TmdbItem tmdbItem, MediaType type) {
    }
}