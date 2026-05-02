package com.riyura.backend.modules.content.controller;

import com.riyura.backend.modules.content.dto.search.SearchResponse;
import com.riyura.backend.modules.content.dto.search.SearchSortOrder;
import com.riyura.backend.modules.content.port.SearchServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Validated
public class SearchController {

    private final SearchServicePort searchService;

    // Handle search requests from the frontend
    @GetMapping
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam("q") @NotBlank @Size(min = 1, max = 200) String query,
            @RequestParam(defaultValue = "0") @Min(0) @Max(500) int page,
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
