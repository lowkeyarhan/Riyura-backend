package com.riyura.backend.modules.identity.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.riyura.backend.modules.identity.dto.history.DeleteWatchHistoryRequest;
import com.riyura.backend.modules.identity.dto.history.HistoryResponse;
import com.riyura.backend.modules.identity.dto.history.HistoryRequest;
import com.riyura.backend.modules.identity.dto.profile.OnboardRequest;
import com.riyura.backend.modules.identity.model.UserProfile;
import com.riyura.backend.modules.identity.service.HistoryService;
import com.riyura.backend.modules.identity.service.ProfileService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final HistoryService historyService;
    private final ProfileService profileService;

    // Fetch the user's profile
    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(@AuthenticationPrincipal Jwt jwt) {
        UserProfile profile = profileService.getProfile(jwt);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", profile);
        return ResponseEntity.ok(response);
    }

    // Fetch the user's onboarding status
    @GetMapping("/onboard")
    public ResponseEntity<Map<String, Object>> getOnboardStatus(@AuthenticationPrincipal Jwt jwt) {
        UserProfile profile = profileService.getProfile(jwt);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("onboarded", profile.getOnboarded());
        response.put("photoUrl", profile.getPhotoUrl());
        return ResponseEntity.ok(response);
    }

    // Update the user's onboarding status
    @PatchMapping("/onboard")
    public ResponseEntity<Map<String, Object>> updateOnboard(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OnboardRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UserProfile profile = profileService.updateOnboarded(userId, request.getOnboarded());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", profile);
        return ResponseEntity.ok(response);
    }

    // Fetch the user's watch history with pagination
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getWatchHistory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page) {
        UUID userId = UUID.fromString(jwt.getSubject());
        log.debug("Fetching watch history for user ID: {}, page: {}", userId, page);

        List<HistoryResponse> history = historyService.getUserWatchHistory(userId, page);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", history);

        return ResponseEntity.ok(response);
    }

    // Add or update a watch history item
    @PostMapping("/history")
    public ResponseEntity<Map<String, Object>> addOrUpdateHistory(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody HistoryRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        historyService.addOrUpdateHistory(userId, request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "History saved successfully");
        return ResponseEntity.ok(response);
    }

    // Delete a watch history item
    @DeleteMapping("/history")
    public ResponseEntity<Map<String, Object>> deleteWatchHistory(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DeleteWatchHistoryRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        historyService.deleteWatchHistory(userId, request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "History deleted successfully");
        return ResponseEntity.ok(response);
    }

}
