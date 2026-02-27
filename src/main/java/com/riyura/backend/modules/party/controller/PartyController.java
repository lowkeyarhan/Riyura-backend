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

@RestController
@RequestMapping("/api/party")
@RequiredArgsConstructor
public class PartyController {

    private final PartyService partyService;

    // Create a new party. The creator becomes the host.
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

    // Get the current details of a party
    @GetMapping("/{partyId}/state")
    public ResponseEntity<Map<String, Object>> getPartyState(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String partyId) {

        PartyStateResponse state = partyService.getState(partyId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", state));
    }

    // Record a heartbeat from a party member to keep the party alive
    @PostMapping("/{partyId}/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String partyId) {

        String userId = jwt.getSubject();
        partyService.recordHeartbeat(partyId, userId);
        return ResponseEntity.ok(Map.of("success", true, "ack", System.currentTimeMillis()));
    }
}
