package com.riyura.backend.modules.content.controller;

import com.riyura.backend.common.dto.media.MediaGridResponse;
import com.riyura.backend.modules.content.port.AnimeServicePort;

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
@RequestMapping("/api/anime")
@RequiredArgsConstructor
public class AnimeController {

    private final AnimeServicePort animeService;

    // Fetch Trending Anime (TV + Movies) - Combined & Sorted by Rating
    @GetMapping("/trending")
    public ResponseEntity<Map<String, List<MediaGridResponse>>> getTrending(
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int limit) {
        List<MediaGridResponse> items = animeService.getTrendingAnime(limit);

        Map<String, List<MediaGridResponse>> response = new HashMap<>();
        response.put("results", items);

        return ResponseEntity.ok(response);
    }
}
