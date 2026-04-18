package com.riyura.backend.modules.content.service.tv;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.riyura.backend.common.config.TmdbProperties;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.common.service.TmdbUrlBuilder;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.modules.content.dto.tv.TvPlayerResponse;
import com.riyura.backend.modules.content.dto.tv.TvShowDetails;
import com.riyura.backend.modules.content.model.Episode;
import com.riyura.backend.modules.content.model.Season;
import com.riyura.backend.modules.content.port.TvPlayerServicePort;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TvPlayerService implements TvPlayerServicePort {

    private final TmdbClient tmdbClient;
    private final TmdbProperties tmdbProperties;

    @Override
    @Cacheable(value = "tvPlayer", key = "#id", sync = true)
    public TvPlayerResponse getTvPlayer(String id) {
        String detailsUrl = TmdbUrlBuilder.from(tmdbProperties)
                .path("/tv/" + id)
                .param("language", "en-US")
                .build();

        try {
            TvShowDetails details = tmdbClient.fetchWithRetry(detailsUrl, TvShowDetails.class);
            return details == null ? null : mapToPlayerResponse(id, details);
        } catch (Exception e) {
            log.error("Error fetching TV player payload for ID {}: {}", id, e.getMessage());
            return null;
        }
    }

    private TvPlayerResponse mapToPlayerResponse(String tvId, TvShowDetails details) {
        TvPlayerResponse response = new TvPlayerResponse();
        response.setTmdbId(details.getTmdbId());
        response.setTitle(details.getTitle());
        response.setOverview(details.getOverview());
        response.setGenres(details.getGenres() == null ? List.of()
                : details.getGenres().stream().map(TvShowDetails.Genre::getName).filter(Objects::nonNull).toList());
        response.setSeasons(fetchSeasonsWithEpisodes(tvId, details.getSeasons()));
        response.setAnime(TmdbUtils.isAnime(details.getOriginalLanguage(), details.getGenres()));
        return response;
    }

    private List<Season> fetchSeasonsWithEpisodes(String tvId, List<Season> seasons) {
        if (seasons == null)
            return List.of();

        List<Season> filteredSeasons = seasons.stream()
                .filter(Objects::nonNull)
                .filter(s -> s.getSeasonNumber() == null || s.getSeasonNumber() != 0)
                .toList();

        List<CompletableFuture<Season>> futures = filteredSeasons.stream()
                .map(season -> CompletableFuture
                        .supplyAsync(() -> fetchSeasonWithEpisodes(tvId, season))
                        .orTimeout(8, TimeUnit.SECONDS))
                .toList();

        return futures.stream().map(CompletableFuture::join).toList();
    }

    private Season fetchSeasonWithEpisodes(String tvId, Season season) {
        try {
            String url = TmdbUrlBuilder.from(tmdbProperties)
                    .path("/tv/" + tvId + "/season/" + season.getSeasonNumber())
                    .param("language", "en-US")
                    .build();
            SeasonDetails fetched = tmdbClient.fetchWithRetry(url, SeasonDetails.class);
            if (fetched != null && fetched.getEpisodes() != null) {
                season.setEpisodes(fetched.getEpisodes());
            }
        } catch (Exception e) {
            log.warn("Could not fetch episodes for season {}: {}", season.getSeasonNumber(), e.getMessage());
        }
        return season;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SeasonDetails {
        private List<Episode> episodes;
    }
}
