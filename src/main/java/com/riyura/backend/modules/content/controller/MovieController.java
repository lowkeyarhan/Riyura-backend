package com.riyura.backend.modules.content.controller;

import com.riyura.backend.common.dto.media.MediaGridResponse;
import com.riyura.backend.modules.content.dto.movie.MovieDetail;
import com.riyura.backend.modules.content.port.MovieServicePort;
import com.riyura.backend.modules.content.port.MovieDetailServicePort;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieServicePort movieService;
    private final MovieDetailServicePort movieDetailsService;

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

    // Helper method to wrap the list in a response map
    private ResponseEntity<Map<String, List<MediaGridResponse>>> wrapResponse(List<MediaGridResponse> list) {
        Map<String, List<MediaGridResponse>> response = new HashMap<>();
        response.put("results", list);
        return ResponseEntity.ok(response);
    }
}
