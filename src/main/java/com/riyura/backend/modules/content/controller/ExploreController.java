package com.riyura.backend.modules.content.controller;

import com.riyura.backend.common.dto.explore.ExploreDto;
import com.riyura.backend.modules.content.service.explore.ExploreService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/explore")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class ExploreController {

    private final ExploreService exploreService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> explore(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String genres,
            @RequestParam(required = false) String language) {

        List<ExploreDto> results = exploreService.getExplorePage(page, genres, language);

        Map<String, Object> response = new HashMap<>();
        response.put("page", page);
        response.put("results", results);

        return ResponseEntity.ok(response);
    }
}
