package com.riyura.backend.modules.identity.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.riyura.backend.common.dto.media.MediaGridResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.identity.dto.watchlist.WatchlistRequest;
import com.riyura.backend.modules.identity.service.WatchlistService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    // Fetch the user's watchlist, ordered by most recent
    @GetMapping
    public ResponseEntity<Map<String, Object>> getWatchlist(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page) {
        UUID userId = UUID.fromString(jwt.getSubject());
        List<MediaGridResponse> data = watchlistService.getUserWatchlist(userId, page);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    // Add a media item to the user's watchlist
    @PostMapping
    public ResponseEntity<Map<String, Object>> addToWatchlist(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WatchlistRequest request) {
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            watchlistService.addToWatchlist(userId, request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Added to watchlist");
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "Watchlist item already exists");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
            }
            throw e;
        }
    }

    // Delete a media item from the user's watchlist
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteFromWatchlist(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WatchlistRequest request) {
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            watchlistService.deleteFromWatchlist(userId, request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Watchlist item deleted successfully");
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "Watchlist item not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }
            throw e;
        }
    }

    // Check if a media item is in the user's watchlist
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkWatchlist(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam Long tmdbId,
            @RequestParam MediaType mediaType) {

        UUID userId = UUID.fromString(jwt.getSubject());
        boolean exists = watchlistService.isInWatchlist(userId, tmdbId, mediaType);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("isInWatchlist", exists);
        return ResponseEntity.ok(response);
    }

}
