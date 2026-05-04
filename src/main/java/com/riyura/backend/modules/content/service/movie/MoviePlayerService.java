package com.riyura.backend.modules.content.service.movie;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.riyura.backend.common.config.TmdbProperties;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.common.service.TmdbUrlBuilder;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.modules.content.dto.movie.MovieDetail;
import com.riyura.backend.modules.content.dto.movie.MoviePlayerResponse;
import org.springframework.cache.annotation.Cacheable;
import com.riyura.backend.modules.content.port.MoviePlayerServicePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MoviePlayerService implements MoviePlayerServicePort {

    private final TmdbClient tmdbClient;
    private final TmdbProperties tmdbProperties;

    @Override
    @Cacheable(value = "moviePlayer", key = "#id", sync = true)
    public MoviePlayerResponse getMoviePlayer(String id) {
        String detailsUrl = TmdbUrlBuilder.from(tmdbProperties)
                .path("/movie/" + id)
                .param("language", "en-US")
                .build();

        try {
            MovieDetail details = tmdbClient.fetchWithRetry(detailsUrl, MovieDetail.class);
            return details == null ? null : mapToPlayerResponse(details);
        } catch (Exception e) {
            log.error("Error fetching movie player payload for ID {}: {}", id, e.getMessage());
            return null;
        }
    }

    private MoviePlayerResponse mapToPlayerResponse(MovieDetail details) {
        MoviePlayerResponse response = new MoviePlayerResponse();
        response.setTmdbId(details.getTmdbId());
        response.setTitle(details.getTitle());
        response.setOverview(details.getOverview());
        response.setGenres(details.getGenres() == null ? List.of()
                : details.getGenres().stream().map(MovieDetail.Genre::getName).filter(Objects::nonNull).toList());
        response.setAnime(TmdbUtils.isAnime(details.getOriginalLanguage(), details.getGenres()));
        response.setBackdropPath(details.getBackdropPath());
        return response;
    }
}
