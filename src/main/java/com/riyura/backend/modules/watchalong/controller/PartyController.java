package com.riyura.backend.modules.watchalong.controller;

import com.riyura.backend.modules.watchalong.dto.party.requests.CreatePartyRequest;
import com.riyura.backend.modules.watchalong.dto.party.requests.JoinPartyRequest;
import com.riyura.backend.modules.watchalong.dto.party.requests.PartyProgressRequest;
import com.riyura.backend.modules.watchalong.dto.party.requests.SendChatRequest;
import com.riyura.backend.modules.watchalong.dto.party.responses.PartyStateResponse;
import com.riyura.backend.modules.watchalong.dto.party.responses.SyncResponse;
import com.riyura.backend.modules.watchalong.interfaces.PartyServicePort;
import com.riyura.backend.modules.watchalong.service.party.SseEmitterRegistry;
import com.riyura.backend.common.util.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/watchalong/party")
@RequiredArgsConstructor
public class PartyController {

    private final PartyServicePort partyService;
    private final SseEmitterRegistry sseEmitterRegistry;

    // Create a new party
    @PostMapping("/create")
    public ResponseEntity<PartyStateResponse> createParty(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePartyRequest request) {
        UserInfo user = extractUser(jwt);
        return ResponseEntity.ok(partyService.createParty(request, user.id(), user.username(), user.avatarUrl()));
    }

    // Join an existing party using its 8-char code
    @PostMapping("/join")
    public ResponseEntity<PartyStateResponse> joinParty(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody JoinPartyRequest request) {
        UserInfo user = extractUser(jwt);
        return ResponseEntity.ok(partyService.joinParty(request, user.id(), user.username(), user.avatarUrl()));
    }

    @PostMapping("/leave")
    public ResponseEntity<Void> leaveParty(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String partyId) {
        partyService.leaveParty(partyId.toUpperCase(), JwtUtils.extractUserId(jwt));
        return ResponseEntity.noContent().build();
    }

    // Host-only: push raw progress + providerId (URL is built lazily on join/sync)
    @PostMapping("/progress")
    public ResponseEntity<Void> pushProgress(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PartyProgressRequest request) {
        partyService.pushProgress(request, JwtUtils.extractUserId(jwt));
        return ResponseEntity.noContent().build();
    }

    // Liveness heartbeat — must be sent every ~2.5 min; also emits HEARTBEAT SSE
    // event
    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String partyId) {
        partyService.sendHeartbeat(partyId.toUpperCase(), JwtUtils.extractUserId(jwt));
        return ResponseEntity.noContent().build();
    }

    // Send a chat message; stored in Redis + broadcast via NEW_CHAT SSE event
    @PostMapping("/chat")
    public ResponseEntity<Void> sendChat(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SendChatRequest request) {
        UserInfo user = extractUser(jwt);
        partyService.sendChat(request, user.id(), user.username(), user.avatarUrl());
        return ResponseEntity.noContent().build();
    }

    // Build and return the current streamUrl at latest progress; does not broadcast
    @GetMapping("/{partyId}/sync")
    public ResponseEntity<SyncResponse> sync(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String partyId) {
        return ResponseEntity.ok(partyService.sync(partyId.toUpperCase(), JwtUtils.extractUserId(jwt)));
    }

    // Get full party metadata + recent chat (streamUrl is built at current
    // progress)
    @GetMapping("/{partyId}")
    public ResponseEntity<PartyStateResponse> getPartyState(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String partyId) {
        return ResponseEntity.ok(partyService.getPartyState(partyId.toUpperCase(), JwtUtils.extractUserId(jwt)));
    }

    // SSE stream: connects once per party join and streams all real-time party
    // events
    // Returns 404/403 as JSON if party doesn't exist or user isn't a participant
    // Usage: GET /api/watchalong/party/events?partyId={8-char-code}
    @GetMapping(value = "/events", produces = { MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<?> streamEvents(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String partyId) {

        String normalizedId = partyId.toUpperCase();
        UUID userId = JwtUtils.extractUserId(jwt);

        // Validate participation BEFORE creating the emitter so errors are plain JSON
        try {
            partyService.getPartyState(normalizedId, userId);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("error", "Party not found: " + normalizedId));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("error", "You are not a participant in this party."));
        }

        SseEmitter emitter = new SseEmitter(2 * 60 * 60 * 1000L); // 2-hour timeout
        sseEmitterRegistry.register(normalizedId, userId, emitter);

        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(Map.of("partyId", normalizedId, "userId", userId.toString())));
        } catch (IOException e) {
            log.warn("Failed to send CONNECTED event to userId={}", userId);
            sseEmitterRegistry.remove(normalizedId, userId);
            emitter.completeWithError(e);
        }

        log.info("SSE stream opened: partyId={}, userId={}", normalizedId, userId);
        return ResponseEntity.ok(emitter);
    }

    // Extract user info from JWT
    private UserInfo extractUser(Jwt jwt) {
        return new UserInfo(
                JwtUtils.extractUserId(jwt),
                JwtUtils.extractName(jwt),
                JwtUtils.extractAvatarUrl(jwt));
    }

    private record UserInfo(UUID id, String username, String avatarUrl) {
    }
}
