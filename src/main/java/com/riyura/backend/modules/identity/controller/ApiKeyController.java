package com.riyura.backend.modules.identity.controller;

import com.riyura.backend.modules.identity.service.GeminiApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/key") // Adjust path or configure Next.js rewrites
@RequiredArgsConstructor
public class ApiKeyController {

    private final GeminiApiKeyService apiKeyService;

    // Retrieves the status of the API key for the authenticated user, including
    // whether it exists and a preview of the key.
    @GetMapping
    public ResponseEntity<?> getApiKeyStatus(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject()); // Assuming Supabase auth JWT
        return ResponseEntity.ok(apiKeyService.getApiKeyStatus(userId));
    }

    // Saves or updates the Gemini API key for the authenticated user. Expects a
    // JSON body with the "apiKey" field. Returns the status and preview of the key.
    @PostMapping
    public ResponseEntity<?> saveApiKey(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, String> body) {
        String apiKey = body.get("apiKey");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "API Key is required"));
        }

        UUID userId = UUID.fromString(jwt.getSubject());
        try {
            return ResponseEntity.ok(apiKeyService.saveApiKey(userId, apiKey));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Deletes the API key for the authenticated user.
    @DeleteMapping
    public ResponseEntity<?> deleteApiKey(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        apiKeyService.deleteApiKey(userId);
        return ResponseEntity.ok().build();
    }

    // ⚠️ TEMPORARY TESTING ENDPOINT - REMOVE BEFORE PRODUCTION ⚠️
    // Fetches the decrypted API key for the authenticated user. This is intended
    // for testing and should not be exposed in production environments.
    // @GetMapping("/raw")
    // public ResponseEntity<?>
    // getRawDecryptedKeyForTesting(@AuthenticationPrincipal Jwt jwt) {
    // UUID userId = UUID.fromString(jwt.getSubject());
    // try {
    // String decryptedKey = apiKeyService.getDecryptedKeyForUser(userId);
    // return ResponseEntity.ok(Map.of(
    // "WARNING", "THIS IS A TEST ENDPOINT. REMOVE BEFORE DEPLOYING TO PRODUCTION.",
    // "decryptedKey", decryptedKey));
    // } catch (Exception e) {
    // return ResponseEntity.badRequest()
    // .body(Map.of("error", "Key not found or decryption failed: " +
    // e.getMessage()));
    // }
    // }
}