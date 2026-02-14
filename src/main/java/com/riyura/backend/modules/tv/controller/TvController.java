package com.riyura.backend.modules.tv.controller;

import com.riyura.backend.common.dto.MediaGridItemDTO;
import com.riyura.backend.modules.tv.service.TvService;
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

    // Get Airing Today TV Shows (Now Playing) with a limit (e.g., top 10)
    @GetMapping("/now-playing")
    public ResponseEntity<Map<String, List<MediaGridItemDTO>>> getNowPlaying(
            @RequestParam(defaultValue = "12") int limit) {
        return wrapResponse(tvService.getAiringToday(limit));
    }

    // Get Trending TV Shows with a limit (e.g., top 10)
    @GetMapping("/trending")
    public ResponseEntity<Map<String, List<MediaGridItemDTO>>> getTrending(
            @RequestParam(defaultValue = "12") int limit) {
        return wrapResponse(tvService.getTrendingTv(limit));
    }

    // Get Popular TV Shows with a limit (e.g., top 10)
    @GetMapping("/popular")
    public ResponseEntity<Map<String, List<MediaGridItemDTO>>> getPopular(
            @RequestParam(defaultValue = "12") int limit) {
        return wrapResponse(tvService.getPopularTv(limit));
    }

    // Get releasing soon TV Shows with a limit (e.g., top 10)
    @GetMapping("/upcoming")
    public ResponseEntity<Map<String, List<MediaGridItemDTO>>> getUpcoming(
            @RequestParam(defaultValue = "12") int limit) {
        return wrapResponse(tvService.getOnTheAir(limit));
    }

    // Helper method to wrap the list in a response map
    private ResponseEntity<Map<String, List<MediaGridItemDTO>>> wrapResponse(List<MediaGridItemDTO> list) {
        Map<String, List<MediaGridItemDTO>> response = new HashMap<>();
        response.put("results", list);
        return ResponseEntity.ok(response);
    }
}