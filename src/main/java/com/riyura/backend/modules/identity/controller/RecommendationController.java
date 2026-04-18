package com.riyura.backend.modules.identity.controller;

import com.riyura.backend.common.config.TmdbProperties;
import com.riyura.backend.modules.identity.dto.recomendation.RecommendationsResponse;
import com.riyura.backend.modules.identity.port.RecommendationServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationServicePort recommendationService;
    private final TmdbProperties tmdbProperties;

    @GetMapping
    public ResponseEntity<?> getRecommendations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean refresh) {

        UUID userId = UUID.fromString(jwt.getSubject());

        try {
            List<RecommendationsResponse> recommendations = recommendationService
                    .getRecommendations(userId, refresh)
                    .stream()
                    .map(r -> RecommendationsResponse.from(r, tmdbProperties.imageBaseUrl()))
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "recommendations", recommendations,
                    "source", refresh ? "generated" : "database"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
