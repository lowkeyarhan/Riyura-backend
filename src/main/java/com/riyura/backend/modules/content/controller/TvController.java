package com.riyura.backend.modules.content.controller;

import com.riyura.backend.common.dto.MediaGridResponse;
import com.riyura.backend.common.dto.StreamProviderRequest;
import com.riyura.backend.common.dto.StreamUrlResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.service.StreamUrlService;
import com.riyura.backend.modules.content.dto.tv.TvPlayerResponse;
import com.riyura.backend.modules.content.dto.tv.TvShowDetails;
import com.riyura.backend.modules.content.service.tv.TvDetailsService;
import com.riyura.backend.modules.content.service.tv.TvPlayerService;
import com.riyura.backend.modules.content.service.tv.TvService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tv")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class TvController {

    private final TvService tvService;
    private final TvDetailsService tvDetailsService;
    private final TvPlayerService tvPlayerService;
    private final StreamUrlService streamUrlService;

    // Get Airing Today TV Shows (Now Playing) with a limit (e.g., top 10)
    @GetMapping("/now-playing")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getNowPlaying(
            @RequestParam(defaultValue = "12") int limit) {
        return wrapResponse(tvService.getAiringToday(limit));
    }

    // Get Trending TV Shows with a limit (e.g., top 10)
    @GetMapping("/trending")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getTrending(
            @RequestParam(defaultValue = "12") int limit) {
        return wrapResponse(tvService.getTrendingTv(limit));
    }

    // Get Popular TV Shows with a limit (e.g., top 10)
    @GetMapping("/popular")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getPopular(
            @RequestParam(defaultValue = "12") int limit) {
        return wrapResponse(tvService.getPopularTv(limit));
    }

    // Get releasing soon TV Shows with a limit (e.g., top 10)
    @GetMapping("/upcoming")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getUpcoming(
            @RequestParam(defaultValue = "12") int limit) {
        return wrapResponse(tvService.getOnTheAir(limit));
    }

    // Get TV Show Details by ID
    @GetMapping("details/{id}")
    public ResponseEntity<TvShowDetails> getTvById(@PathVariable String id) {
        TvShowDetails details = tvDetailsService.getTvDetails(id);
        if (details == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(details);
    }

    // Get Similar TV Shows by TV ID (top 6 by vote average)
    @GetMapping("details/{id}/similar")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getSimilarTvShows(@PathVariable String id) {
        return wrapResponse(tvDetailsService.getSimilarTvShows(id));
    }

    @GetMapping("/player/{id}")
    public ResponseEntity<TvPlayerResponse> getTvPlayer(@PathVariable String id) {
        TvPlayerResponse playerResponse = tvPlayerService.getTvPlayer(id);
        if (playerResponse == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(playerResponse);
    }


    // Build fully-constructed stream URLs for a specific TV show episode
    @PostMapping("/stream")
    public ResponseEntity<List<StreamUrlResponse>> getTvStream(@RequestBody StreamProviderRequest request) {
        return ResponseEntity.ok(streamUrlService.buildStreamUrls(request, MediaType.TV));
    }

    // Helper method to wrap the list in a response map
    private ResponseEntity<Map<String, List<MediaGridResponse>>> wrapResponse(List<MediaGridResponse> list) {
        Map<String, List<MediaGridResponse>> response = new HashMap<>();
        response.put("results", list);
        return ResponseEntity.ok(response);
    }
}
