package com.riyura.backend.modules.content.service.tv;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.modules.content.dto.tv.TvPlayerResponse;
import com.riyura.backend.modules.content.dto.tv.TvShowDetails;
import com.riyura.backend.modules.content.model.Episode;
import com.riyura.backend.modules.content.model.Season;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TvPlayerService {

    private final TmdbClient tmdbClient;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    public TvPlayerResponse getTvPlayer(String id) {
        String detailsUrl = String.format("%s/tv/%s?api_key=%s&language=en-US", baseUrl, id, apiKey);

        try {
            TvShowDetails details = tmdbClient.fetchWithRetry(detailsUrl, TvShowDetails.class);
            return details == null ? null : mapToPlayerResponse(id, details);
        } catch (Exception e) {
            System.err.println("Error fetching TV player payload for ID " + id + ": " + TmdbClient.rootMessage(e));
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

    // Fetch each season's episodes in parallel and merge them into the Season
    // models.
    private List<Season> fetchSeasonsWithEpisodes(String tvId, List<Season> seasons) {
        if (seasons == null)
            return List.of();

        List<Season> filteredSeasons = seasons.stream()
                .filter(Objects::nonNull)
                .filter(s -> s.getSeasonNumber() == null || s.getSeasonNumber() != 0)
                .toList();

        List<CompletableFuture<Season>> futures = filteredSeasons.stream()
                .map(season -> CompletableFuture.supplyAsync(() -> fetchSeasonWithEpisodes(tvId, season)))
                .toList();

        return futures.stream().map(CompletableFuture::join).toList();
    }

    private Season fetchSeasonWithEpisodes(String tvId, Season season) {
        try {
            String url = String.format("%s/tv/%s/season/%d?api_key=%s&language=en-US",
                    baseUrl, tvId, season.getSeasonNumber(), apiKey);
            SeasonDetails fetched = tmdbClient.fetch(url, SeasonDetails.class);
            if (fetched != null && fetched.getEpisodes() != null) {
                season.setEpisodes(fetched.getEpisodes());
            }
        } catch (Exception e) {
            // Non-critical — season returns without episodes rather than failing the whole
            // request
            System.err
                    .println("Could not fetch episodes for season " + season.getSeasonNumber() + ": " + e.getMessage());
        }
        return season;
    }

    // Minimal response shape from TMDB's /tv/{id}/season/{season_number} endpoint.
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SeasonDetails {
        private List<Episode> episodes;
    }
}
