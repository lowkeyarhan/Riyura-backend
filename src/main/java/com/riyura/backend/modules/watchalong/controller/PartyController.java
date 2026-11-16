package com.riyura.backend.modules.watchalong.controller;

import com.riyura.backend.common.util.JwtUtils;
import com.riyura.backend.modules.watchalong.dto.party.requests.CreatePartyRequest;
import com.riyura.backend.modules.watchalong.dto.party.requests.JoinPartyRequest;
import com.riyura.backend.modules.watchalong.dto.party.requests.PartyProgressRequest;
import com.riyura.backend.modules.watchalong.dto.party.requests.SendChatRequest;
import com.riyura.backend.modules.watchalong.dto.party.responses.PartyStateResponse;
import com.riyura.backend.modules.watchalong.dto.party.responses.SyncResponse;
import com.riyura.backend.modules.watchalong.interfaces.PartyServicePort;
import com.riyura.backend.modules.watchalong.model.party.Messages;
import com.riyura.backend.modules.watchalong.service.party.SseEmitterRegistry;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
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

@Slf4j
@Validated
@RestController
@RequestMapping("/api/watchalong/party")
@RequiredArgsConstructor
public class PartyController {

    private final PartyServicePort partyService;
    private final SseEmitterRegistry sseEmitterRegistry;

    @PostMapping("/create")
    public ResponseEntity<PartyStateResponse> createParty(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreatePartyRequest request
    ) {
        UserInfo user = extractUser(jwt);
        return ResponseEntity.ok(
            partyService.createParty(
                request,
                user.id(),
                user.username(),
                user.avatarUrl()
            )
        );
    }

    @PostMapping("/join")
    public ResponseEntity<PartyStateResponse> joinParty(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody JoinPartyRequest request
    ) {
        UserInfo user = extractUser(jwt);
        return ResponseEntity.ok(
            partyService.joinParty(
                request,
                user.id(),
                user.username(),
                user.avatarUrl()
            )
        );
    }

    @PostMapping("/leave")
    public ResponseEntity<Void> leaveParty(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam String partyId
    ) {
        partyService.leaveParty(
            partyId.toUpperCase(),
            JwtUtils.extractUserId(jwt)
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/progress")
    public ResponseEntity<Void> pushProgress(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody PartyProgressRequest request
    ) {
        partyService.pushProgress(request, JwtUtils.extractUserId(jwt));
        return ResponseEntity.noContent().build();
    }

    // Must be sent every ~2.5 min or the participant is evicted.
    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam String partyId
    ) {
        partyService.sendHeartbeat(
            partyId.toUpperCase(),
            JwtUtils.extractUserId(jwt)
        );
        return ResponseEntity.noContent().build();
    }

    // Idempotent; returns the stored message so the sender renders without the SSE echo.
    @PostMapping("/chat")
    public ResponseEntity<Messages> sendChat(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody SendChatRequest request
    ) {
        UserInfo user = extractUser(jwt);
        return ResponseEntity.ok(
            partyService.sendChat(
                request,
                user.id(),
                user.username(),
                user.avatarUrl()
            )
        );
    }

    @GetMapping("/{partyId}/sync")
    public ResponseEntity<SyncResponse> sync(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String partyId
    ) {
        return ResponseEntity.ok(
            partyService.sync(
                partyId.toUpperCase(),
                JwtUtils.extractUserId(jwt)
            )
        );
    }

    @GetMapping("/{partyId}")
    public ResponseEntity<PartyStateResponse> getPartyState(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String partyId
    ) {
        return ResponseEntity.ok(
            partyService.getPartyState(
                partyId.toUpperCase(),
                JwtUtils.extractUserId(jwt)
            )
        );
    }

    // SSE stream for one party. 404/403 are plain JSON, not a broken stream.
    @GetMapping(
        value = "/events",
        produces = {
            MediaType.TEXT_EVENT_STREAM_VALUE,
            MediaType.APPLICATION_JSON_VALUE,
        }
    )
    public ResponseEntity<?> streamEvents(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam String partyId
    ) {
        String normalizedId = partyId.toUpperCase();
        UUID userId = JwtUtils.extractUserId(jwt);

        SseEmitter emitter = new SseEmitter(2 * 60 * 60 * 1000L);
        // Register before reading history, so a message sent during the read is not lost.
        UUID connectionId = sseEmitterRegistry.register(
            normalizedId,
            userId,
            emitter
        );

        PartyStateResponse snapshot;
        try {
            snapshot = partyService.getPartyState(normalizedId, userId);
        } catch (NoSuchElementException e) {
            sseEmitterRegistry.remove(normalizedId, connectionId);
            return ResponseEntity.status(404).body(
                Map.of("error", "Party not found: " + normalizedId)
            );
        } catch (AccessDeniedException e) {
            sseEmitterRegistry.remove(normalizedId, connectionId);
            return ResponseEntity.status(403).body(
                Map.of("error", "You are not a participant in this party.")
            );
        }

        try {
            emitter.send(
                SseEmitter.event()
                    .name("CONNECTED")
                    .data(
                        Map.of(
                            "partyId",
                            normalizedId,
                            "userId",
                            userId.toString(),
                            "recentMessages",
                            snapshot.getRecentMessages()
                        )
                    )
            );
        } catch (IOException e) {
            log.warn("Failed to send CONNECTED event to userId={}", userId);
            sseEmitterRegistry.remove(normalizedId, connectionId);
            emitter.completeWithError(e);
        }

        log.info(
            "SSE stream opened: partyId={}, userId={}",
            normalizedId,
            userId
        );
        return ResponseEntity.ok()
            .header("Cache-Control", "no-cache, no-transform")
            .header("X-Accel-Buffering", "no")
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(emitter);
    }

    private UserInfo extractUser(Jwt jwt) {
        return new UserInfo(
            JwtUtils.extractUserId(jwt),
            JwtUtils.extractName(jwt),
            JwtUtils.extractAvatarUrl(jwt)
        );
    }

    private record UserInfo(UUID id, String username, String avatarUrl) {}
}
