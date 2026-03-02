package com.riyura.backend.modules.identity.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

import com.riyura.backend.modules.identity.dto.history.DeleteWatchHistoryRequest;
import com.riyura.backend.modules.identity.dto.history.HistoryResponse;
import com.riyura.backend.modules.identity.dto.history.HistoryRequest;
import com.riyura.backend.modules.identity.dto.profile.OnboardRequest;
import com.riyura.backend.modules.identity.model.UserProfile;
import com.riyura.backend.modules.identity.model.WatchHistory;
import com.riyura.backend.modules.identity.service.HistoryService;
import com.riyura.backend.modules.identity.service.ProfileService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

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

    // Fetch the user's watch history
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getWatchHistory(@AuthenticationPrincipal Jwt jwt) {
        // Extract User ID from the Supabase Token
        String userIdString = jwt.getSubject();
        UUID userId = UUID.fromString(userIdString);
        log.debug("Fetching watch history for user ID: {}", userId);

        List<HistoryResponse> history = historyService.getUserWatchHistory(userId);

        // Wrap in "data" object to generic API responses often used
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
        Map<String, Object> response = new HashMap<>();
        UUID userId = UUID.fromString(jwt.getSubject());
        WatchHistory data = historyService.addOrUpdateHistory(userId, request);
        response.put("success", true);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    // Delete a watch history item
    @DeleteMapping("/history")
    public ResponseEntity<Map<String, Object>> deleteWatchHistory(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DeleteWatchHistoryRequest request) {
        Map<String, Object> response = new HashMap<>();
        UUID userId = UUID.fromString(jwt.getSubject());
        historyService.deleteWatchHistory(userId, request);
        response.put("success", true);
        response.put("message", "History deleted successfully");
        return ResponseEntity.ok(response);
    }

}
