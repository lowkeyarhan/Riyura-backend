package com.riyura.backend.modules.content.controller;

import com.riyura.backend.common.dto.MediaGridResponse;
import com.riyura.backend.modules.content.service.anime.AnimeService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/anime")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class AnimeController {

    private final AnimeService animeService;

    // Fetch Trending Anime (TV + Movies) - Combined & Sorted by Rating
    @GetMapping("/trending")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getTrending(
            @RequestParam(defaultValue = "12") int limit) {
        List<MediaGridResponse> items = animeService.getTrendingAnime(limit);

        Map<String, List<MediaGridResponse>> response = new HashMap<>();
        response.put("results", items);

        return ResponseEntity.ok(response);
    }
}