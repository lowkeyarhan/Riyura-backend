package com.riyura.backend.modules.content.controller;

import com.riyura.backend.common.dto.media.MediaGridResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.content.dto.stream.StreamProviderRequest;
import com.riyura.backend.modules.content.dto.stream.StreamUrlResponse;
import com.riyura.backend.modules.content.dto.tv.TvPlayerResponse;
import com.riyura.backend.modules.content.dto.tv.TvShowDetails;
import com.riyura.backend.modules.content.service.stream.StreamUrlService;
import com.riyura.backend.modules.content.service.tv.TvDetailsService;
import com.riyura.backend.modules.content.service.tv.TvPlayerService;
import com.riyura.backend.modules.content.service.tv.TvService;

import jakarta.validation.Valid;
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
@RequestMapping("/api/tv")
@RequiredArgsConstructor
public class TvController {

    private final TvService tvService;
    private final TvDetailsService tvDetailsService;
    private final TvPlayerService tvPlayerService;
    private final StreamUrlService streamUrlService;

    // Get Airing Today TV Shows (Now Playing) with a limit (e.g., top 12)
    @GetMapping("/now-playing")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getNowPlaying(
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int limit) {
        return wrapResponse(tvService.getAiringToday(limit));
    }

    // Get Trending TV Shows with a limit (e.g., top 12)
    @GetMapping("/trending")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getTrending(
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int limit) {
        return wrapResponse(tvService.getTrendingTv(limit));
    }

    // Get Popular TV Shows with a limit (e.g., top 12)
    @GetMapping("/popular")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getPopular(
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int limit) {
        return wrapResponse(tvService.getPopularTv(limit));
    }

    // Get releasing soon TV Shows with a limit (e.g., top 12)
    @GetMapping("/upcoming")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getUpcoming(
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int limit) {
        return wrapResponse(tvService.getOnTheAir(limit));
    }

    // Get TV Show Details by ID
    @GetMapping("details/{id}")
    public ResponseEntity<TvShowDetails> getTvById(@PathVariable Long id) {
        TvShowDetails details = tvDetailsService.getTvDetails(String.valueOf(id));
        if (details == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(details);
    }

    // Get Similar TV Shows by TV ID (top 6 by vote average)
    @GetMapping("details/{id}/similar")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getSimilarTvShows(@PathVariable Long id) {
        return wrapResponse(tvDetailsService.getSimilarTvShows(String.valueOf(id)));
    }

    @GetMapping("/player/{id}")
    public ResponseEntity<TvPlayerResponse> getTvPlayer(@PathVariable Long id) {
        TvPlayerResponse playerResponse = tvPlayerService.getTvPlayer(String.valueOf(id));
        if (playerResponse == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(playerResponse);
    }

    // Build fully-constructed stream URLs for a specific TV show episode
    @PostMapping("/stream")
    public ResponseEntity<List<StreamUrlResponse>> getTvStream(
            @Valid @RequestBody StreamProviderRequest request) {
        return ResponseEntity.ok(streamUrlService.buildStreamUrls(request, MediaType.TV));
    }

    // Helper method to wrap the list in a response map
    private ResponseEntity<Map<String, List<MediaGridResponse>>> wrapResponse(List<MediaGridResponse> list) {
        Map<String, List<MediaGridResponse>> response = new HashMap<>();
        response.put("results", list);
        return ResponseEntity.ok(response);
    }
}
