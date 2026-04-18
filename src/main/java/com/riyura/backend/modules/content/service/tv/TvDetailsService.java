package com.riyura.backend.modules.content.service.tv;

import com.riyura.backend.common.config.CacheStampedeGuard;
import com.riyura.backend.common.config.TmdbProperties;
import com.riyura.backend.common.dto.media.MediaGridResponse;
import com.riyura.backend.common.dto.tmdb.TmdbTrendingResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.common.service.TmdbUrlBuilder;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.modules.content.dto.global.CastResponse;
import com.riyura.backend.modules.content.dto.tv.TvShowDetails;
import com.riyura.backend.modules.content.port.TvDetailsServicePort;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TvDetailsService implements TvDetailsServicePort {

    private static final int SIMILAR_LIMIT = 6;

    private final TmdbClient tmdbClient;
    private final CacheStampedeGuard cacheStampedeGuard;
    private final TmdbProperties tmdbProperties;

    @Override
    public TvShowDetails getTvDetails(String id) {
        return cacheStampedeGuard.xfetch(
                "tvDetails:" + id, Duration.ofDays(7), 1.5,
                () -> {
                    String detailsUrl = TmdbUrlBuilder.from(tmdbProperties)
                            .path("/tv/" + id)
                            .param("language", "en-US")
                            .build();
                    String creditsUrl = TmdbUrlBuilder.from(tmdbProperties)
                            .path("/tv/" + id + "/credits")
                            .param("language", "en-US")
                            .build();
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

                        TvShowDetails details = detailsTask.orTimeout(8, TimeUnit.SECONDS).join();
                        CreditsResponse credits = creditsTask.orTimeout(8, TimeUnit.SECONDS).join();
                        if (details != null) {
                            details.setCasts(credits != null && credits.getCast() != null
                                    ? credits.getCast()
                                    : Collections.emptyList());
                            details.setAnime(TmdbUtils.isAnime(details.getOriginalLanguage(), details.getGenres()));
                            details.setMaturityRating(details.isAdult() ? "A" : "U/A");
                        }
                        return details;
                    } catch (Exception e) {
                        log.error("Error fetching TV details for ID {}: {}", id, e.getMessage());
                        return null;
                    }
                });
    }

    @Override
    public List<MediaGridResponse> getSimilarTvShows(String id) {
        return cacheStampedeGuard.xfetch(
                "tvSimilar:" + id, Duration.ofDays(7), 1.0,
                () -> {
                    String similarUrl = TmdbUrlBuilder.from(tmdbProperties)
                            .path("/tv/" + id + "/similar")
                            .param("language", "en-US")
                            .param("page", 1)
                            .build();
                    try {
                        TmdbTrendingResponse response = tmdbClient.fetchWithRetry(similarUrl,
                                TmdbTrendingResponse.class);
                        if (response == null || response.getResults() == null)
                            return Collections.emptyList();

                        return response.getResults().stream()
                                .sorted(Comparator.comparing(TmdbTrendingResponse.TmdbItem::getVoteAverage,
                                        Comparator.nullsLast(Comparator.reverseOrder())))
                                .limit(SIMILAR_LIMIT)
                                .map(this::mapSimilarTvToDTO)
                                .toList();
                    } catch (Exception e) {
                        log.error("Error fetching similar TV shows for ID {}: {}", id, e.getMessage());
                        return Collections.emptyList();
                    }
                });
    }

    private MediaGridResponse mapSimilarTvToDTO(TmdbTrendingResponse.TmdbItem item) {
        MediaGridResponse dto = new MediaGridResponse();
        dto.setTmdbId(item.getId());
        dto.setTitle(item.getName());
        dto.setYear(TmdbUtils.extractYear(item.getFirstAirDate()));
        dto.setMediaType(MediaType.TV);
        if (item.getPosterPath() != null && !item.getPosterPath().isEmpty()) {
            dto.setPosterUrl(tmdbProperties.imageBaseUrl() + item.getPosterPath());
        }
        return dto;
    }

    @Data
    private static class CreditsResponse {
        private List<CastResponse> cast;
    }
}
