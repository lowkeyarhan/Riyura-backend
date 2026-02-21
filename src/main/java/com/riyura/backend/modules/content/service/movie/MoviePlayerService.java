package com.riyura.backend.modules.content.service.movie;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.modules.content.dto.movie.MovieDetail;
import com.riyura.backend.modules.content.dto.movie.MoviePlayerResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MoviePlayerService {

    private final TmdbClient tmdbClient;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    // Fetches the necessary information to play a movie, including its title,
    // overview,
    public MoviePlayerResponse getMoviePlayer(String id) {
        String detailsUrl = String.format("%s/movie/%s?api_key=%s&language=en-US", baseUrl, id, apiKey);

        try {
            MovieDetail details = tmdbClient.fetchWithRetry(detailsUrl, MovieDetail.class);
            return details == null ? null : mapToPlayerResponse(details);
        } catch (Exception e) {
            System.err.println("Error fetching movie player payload for ID " + id + ": " + TmdbClient.rootMessage(e));
            return null;
        }
    }

    // Maps MovieDetail to MoviePlayerResponse, extracting genres and determining if
    // it's an anime
    private MoviePlayerResponse mapToPlayerResponse(MovieDetail details) {
        MoviePlayerResponse response = new MoviePlayerResponse();
        response.setTmdbId(details.getTmdbId());
        response.setTitle(details.getTitle());
        response.setOverview(details.getOverview());
        response.setGenres(details.getGenres() == null ? List.of()
                : details.getGenres().stream().map(MovieDetail.Genre::getName).filter(Objects::nonNull).toList());
        response.setAnime(TmdbUtils.isAnime(details.getOriginalLanguage(), details.getGenres()));
        return response;
    }
}
