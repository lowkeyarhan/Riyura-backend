package com.riyura.backend.modules.party.controller;

import com.riyura.backend.modules.party.dto.PartyCreateRequest;
import com.riyura.backend.modules.party.dto.PartyStateResponse;
import com.riyura.backend.modules.party.model.PartyState;
import com.riyura.backend.modules.party.service.PartyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoints for Watch Party lifecycle management.
 *
 * POST /api/party/create → create a party, returns partyId
 * GET /api/party/{id}/state → fetch current state (for late joiners)
 * POST /api/party/{id}/heartbeat → extend TTL, update last-seen timestamp
 */
@RestController
@RequestMapping("/api/party")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class PartyController {

    private final PartyService partyService;

    /**
     * Creates a new Watch Party.
     * The authenticated user becomes the host.
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createParty(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PartyCreateRequest request) {

        String hostId = jwt.getSubject();
        PartyState state = partyService.createParty(hostId, request);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "partyId", state.getPartyId()));
    }

    /**
     * Fetches the current party state.
     * Late joiners use the returned {@code serverTime} + {@code partyStartedAt} +
     * {@code startAt}
     * to compute the current playback position before rendering the player.
     */
    @GetMapping("/{partyId}/state")
    public ResponseEntity<Map<String, Object>> getPartyState(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String partyId) {

        PartyStateResponse state = partyService.getState(partyId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", state));
    }

    /**
     * Heartbeat endpoint — the frontend should call this every 15 seconds.
     * Extends the Redis TTL to prevent zombie-party expiry.
     */
    @PostMapping("/{partyId}/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String partyId) {

        String userId = jwt.getSubject();
        partyService.recordHeartbeat(partyId, userId);
        return ResponseEntity.ok(Map.of("success", true, "ack", System.currentTimeMillis()));
    }
}
