package com.riyura.backend.modules.content.controller;

import com.riyura.backend.modules.content.dto.search.SearchResponse;
import com.riyura.backend.modules.content.dto.search.SearchSortOrder;
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
public class SearchController {

    private final SearchService searchService;

    // Handle search requests from the frontend
    @GetMapping
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "sort_by", required = false) SearchSortOrder sortBy) {
        // Fetch the search results from the service
        List<SearchResponse> results = searchService.search(query, page, sortBy);
        // Prepare the response
        Map<String, Object> response = new HashMap<>();
        response.put("results", results);
        response.put("page", page);
        return ResponseEntity.ok(response);
    }
}
