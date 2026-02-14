package com.riyura.backend.modules.profile;

import com.riyura.backend.modules.profile.history.dto.WatchHistoryDTO;
import com.riyura.backend.modules.profile.history.service.HistoryService;
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

        List<WatchHistoryDTO> history = historyService.getUserWatchHistory(userId);

        // Wrap in "data" object to generic API responses often used
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", history);

        return ResponseEntity.ok(response);
    }
}