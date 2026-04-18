package com.riyura.backend.modules.content.controller;

import com.riyura.backend.common.dto.media.MediaGridResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.content.dto.movie.MovieDetail;
import com.riyura.backend.modules.content.dto.movie.MoviePlayerResponse;
import com.riyura.backend.modules.content.dto.stream.StreamProviderRequest;
import com.riyura.backend.modules.content.dto.stream.StreamUrlResponse;
import com.riyura.backend.modules.content.port.MovieServicePort;
import com.riyura.backend.modules.content.port.MovieDetailServicePort;
import com.riyura.backend.modules.content.port.MoviePlayerServicePort;
import com.riyura.backend.modules.content.port.StreamUrlServicePort;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieServicePort movieService;
    private final MovieDetailServicePort movieDetailsService;
    private final MoviePlayerServicePort moviePlayerService;
    private final StreamUrlServicePort streamUrlService;

    // Get Now Playing Movies with a limit (e.g., top 12)
    @GetMapping("/now-playing")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getNowPlaying(
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int limit) {
        return wrapResponse(movieService.getNowPlayingMovies(limit));
    }

    // Get Trending Movies with a limit (e.g., top 12)
    @GetMapping("/trending")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getTrending(
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int limit) {
        return wrapResponse(movieService.getTrendingMovies(limit));
    }

    // Get Popular Movies with a limit (e.g., top 12)
    @GetMapping("/popular")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getPopular(
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int limit) {
        return wrapResponse(movieService.getPopularMovies(limit));
    }

    // Get Upcoming Movies with a limit (e.g., top 12)
    @GetMapping("/upcoming")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getUpcoming(
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int limit) {
        return wrapResponse(movieService.getUpcomingMovies(limit));
    }

    // Get Movie Details by ID
    @GetMapping("details/{id}")
    public ResponseEntity<MovieDetail> getMovieById(@PathVariable Long id) {
        MovieDetail details = movieDetailsService.getMovieDetails(String.valueOf(id));
        if (details == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(details);
    }

    // Get Similar Movies by Movie ID (top 6 by vote average)
    @GetMapping("details/{id}/similar")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getSimilarMovies(@PathVariable Long id) {
        return wrapResponse(movieDetailsService.getSimilarMovies(String.valueOf(id)));
    }

    // Get Movie Player Info by ID
    @GetMapping("/player/{id}")
    public ResponseEntity<MoviePlayerResponse> getMoviePlayer(@PathVariable Long id) {
        MoviePlayerResponse playerResponse = moviePlayerService.getMoviePlayer(String.valueOf(id));
        if (playerResponse == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(playerResponse);
    }

    // Build fully-constructed stream URLs for a specific movie
    @PostMapping("/stream")
    public ResponseEntity<List<StreamUrlResponse>> getMovieStream(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StreamProviderRequest request) {
        UUID userId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;
        return ResponseEntity.ok(streamUrlService.buildStreamUrls(request, MediaType.Movie, userId));
    }

    // Helper method to wrap the list in a response map
    private ResponseEntity<Map<String, List<MediaGridResponse>>> wrapResponse(List<MediaGridResponse> list) {
        Map<String, List<MediaGridResponse>> response = new HashMap<>();
        response.put("results", list);
        return ResponseEntity.ok(response);
    }
}
