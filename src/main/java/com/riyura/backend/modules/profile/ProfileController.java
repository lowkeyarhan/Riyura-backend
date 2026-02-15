package com.riyura.backend.modules.profile;

import com.riyura.backend.modules.profile.history.dto.DeleteWatchHistoryRequest;
import com.riyura.backend.modules.profile.history.dto.WatchHistoryRequest;
import com.riyura.backend.modules.profile.history.model.WatchHistory;
import com.riyura.backend.modules.profile.history.service.HistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class ProfileController {

    private final HistoryService historyService;

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getWatchHistory(@AuthenticationPrincipal Jwt jwt) {
        // Extract User ID from the Supabase Token
        String userIdString = jwt.getSubject();
        UUID userId = UUID.fromString(userIdString);
        System.out.println("Fetching watch history for user ID: " + userId);

        List<WatchHistory> history = historyService.getUserWatchHistory(userId);

        // Wrap in "data" object to generic API responses often used
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", history);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/history")
    public ResponseEntity<Map<String, Object>> addOrUpdateHistory(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WatchHistoryRequest request) {
        Map<String, Object> response = new HashMap<>();
        UUID userId = UUID.fromString(jwt.getSubject());
        WatchHistory data = historyService.addOrUpdateHistory(userId, request);
        response.put("success", true);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

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
