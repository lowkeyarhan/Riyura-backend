package com.riyura.backend.modules.content.controller;

import com.riyura.backend.common.dto.MediaGridResponse;
import com.riyura.backend.common.dto.StreamProviderRequest;
import com.riyura.backend.common.dto.StreamUrlResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.service.StreamUrlService;
import com.riyura.backend.modules.content.dto.movie.MovieDetail;
import com.riyura.backend.modules.content.dto.movie.MoviePlayerResponse;
import com.riyura.backend.modules.content.service.movie.MovieDetailService;
import com.riyura.backend.modules.content.service.movie.MoviePlayerService;
import com.riyura.backend.modules.content.service.movie.MovieService;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class MovieController {

    @Autowired
    private final MovieService movieService;

    @Autowired
    private final MovieDetailService movieDetailsService;

    @Autowired
    private final MoviePlayerService moviePlayerService;

    @Autowired
    private final StreamUrlService streamUrlService;

    // Get Now Playing Movies with a limit (e.g., top 10)
    @GetMapping("/now-playing")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getNowPlaying(
            @RequestParam(defaultValue = "12") int limit) {
        return wrapResponse(movieService.getNowPlayingMovies(limit));
    }

    // Get Trending Movies with a limit (e.g., top 10)
    @GetMapping("/trending")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getTrending(
            @RequestParam(defaultValue = "12") int limit) {
        return wrapResponse(movieService.getTrendingMovies(limit));
    }

    // Get Popular Movies with a limit (e.g., top 10)
    @GetMapping("/popular")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getPopular(
            @RequestParam(defaultValue = "12") int limit) {
        return wrapResponse(movieService.getPopularMovies(limit));
    }

    // Get Upcoming Movies with a limit (e.g., top 10)
    @GetMapping("/upcoming")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getUpcoming(
            @RequestParam(defaultValue = "12") int limit) {
        return wrapResponse(movieService.getUpcomingMovies(limit));
    }

    // Get Movie Details by ID
    @GetMapping("details/{id}")
    public ResponseEntity<MovieDetail> getMovieById(@PathVariable String id) {
        MovieDetail details = movieDetailsService.getMovieDetails(id);
        if (details == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(details);
    }

    // Get Similar Movies by Movie ID (top 6 by vote average)
    @GetMapping("details/{id}/similar")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getSimilarMovies(@PathVariable String id) {
        return wrapResponse(movieDetailsService.getSimilarMovies(id));
    }

    // Get Movie Player Info by ID
    @GetMapping("/player/{id}")
    public ResponseEntity<MoviePlayerResponse> getMoviePlayer(@PathVariable String id) {
        MoviePlayerResponse playerResponse = moviePlayerService.getMoviePlayer(id);
        if (playerResponse == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(playerResponse);
    }


    // Build fully-constructed stream URLs for a specific movie
    @PostMapping("/stream")
    public ResponseEntity<List<StreamUrlResponse>> getMovieStream(@RequestBody StreamProviderRequest request) {
        return ResponseEntity.ok(streamUrlService.buildStreamUrls(request, MediaType.Movie));
    }

    // Helper method to wrap the list in a response map
    private ResponseEntity<Map<String, List<MediaGridResponse>>> wrapResponse(List<MediaGridResponse> list) {
        Map<String, List<MediaGridResponse>> response = new HashMap<>();
        response.put("results", list);
        return ResponseEntity.ok(response);
    }
}
