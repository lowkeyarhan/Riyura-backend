package com.riyura.backend.modules.content.controller;

import com.riyura.backend.modules.content.dto.search.SearchResponse;
import com.riyura.backend.modules.content.service.search.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class SearchController {

    private final SearchService searchService;

    // Endpoint to handle search requests from the frontend
    @GetMapping
    public ResponseEntity<Map<String, List<SearchResponse>>> search(@RequestParam("q") String query) {
        List<SearchResponse> results = searchService.search(query);
        Map<String, List<SearchResponse>> response = new HashMap<>();
        response.put("results", results);
        return ResponseEntity.ok(response);
    }
}