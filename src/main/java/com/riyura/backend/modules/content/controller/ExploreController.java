package com.riyura.backend.modules.content.controller;

import com.riyura.backend.modules.content.dto.explore.ExploreResponse;
import com.riyura.backend.modules.content.port.ExploreServicePort;

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
@RequestMapping("/api/explore")
@RequiredArgsConstructor
public class ExploreController {

    private final ExploreServicePort exploreService;

    // Handle explore requests from the frontend
    @GetMapping
    public ResponseEntity<Map<String, Object>> explore(
            @RequestParam(defaultValue = "1") @Min(1) @Max(500) int page,
            @RequestParam(required = false) String genres,
            @RequestParam(required = false) String language) {

        // Fetch the explore results from the service
        List<ExploreResponse> results = exploreService.getExplorePage(page, genres, language);

        // Prepare the response
        Map<String, Object> response = new HashMap<>();
        response.put("page", page);
        response.put("results", results);

        return ResponseEntity.ok(response);
    }
}
