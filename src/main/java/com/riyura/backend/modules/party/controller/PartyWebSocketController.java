package com.riyura.backend.modules.party.controller;

import com.riyura.backend.modules.party.dto.ChatMessage;
import com.riyura.backend.modules.party.dto.PartyMessage;
import com.riyura.backend.modules.party.dto.SyncCommand;
import com.riyura.backend.modules.party.model.PartyEvent;
import com.riyura.backend.modules.party.model.PartyState;
import com.riyura.backend.modules.party.security.WebSocketAuthInterceptor;
import com.riyura.backend.modules.party.service.PartyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.Map;

/**
 * Handles all STOMP @MessageMapping destinations for the Watch Party.
 *
 * <pre>
 * /app/party/{id}/join               → USER_JOINED broadcast
 * /app/party/{id}/sync               → SYNC broadcast (host only)
 * /app/party/{id}/chat               → CHAT broadcast
 * /app/party/{id}/buffering          → FORCE_PAUSE if strictSync
 * /app/party/{id}/buffering-complete → RESUME if strictSync & none buffering
 * /app/party/{id}/toggle-strict-sync → STRICT_SYNC_TOGGLED (host only)
 * /app/party/{id}/heartbeat-ws       → HEARTBEAT_ACK (zombie eviction side-effect)
 * </pre>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PartyWebSocketController {

    private final PartyService partyService;
    private final SimpMessagingTemplate messaging;

    // ─── Join ────────────────────────────────────────────────────────────────

    @MessageMapping("/party/{partyId}/join")
    public void join(@DestinationVariable String partyId,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(headerAccessor);
        PartyState state = partyService.addParticipant(partyId, userId);

        // Store partyId in session so the disconnect listener knows which party the
        // user was in
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        if (attrs != null)
            attrs.put("partyId", partyId);

        broadcast(partyId, new PartyMessage(
                PartyEvent.USER_JOINED,
                Map.of("userId", userId, "participantIds", state.getParticipantIds()),
                userId,
                now()));
    }

    // ─── Sync (host only) ─────────────────────────────────────────────────────

    @MessageMapping("/party/{partyId}/sync")
    public void sync(@DestinationVariable String partyId,
            @Payload SyncCommand command,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(headerAccessor);
        PartyState state = partyService.applySeek(partyId, userId, command.getStartAt(), command.getClientTime());

        broadcast(partyId, new PartyMessage(
                PartyEvent.SYNC,
                Map.of(
                        "startAt", state.getStartAt(),
                        "partyStartedAt", state.getPartyStartedAt()),
                userId,
                now()));
    }

    // ─── Chat ─────────────────────────────────────────────────────────────────

    @MessageMapping("/party/{partyId}/chat")
    public void chat(@DestinationVariable String partyId,
            @Payload ChatMessage incomingMessage,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(headerAccessor);
        incomingMessage.setSenderId(userId);
        incomingMessage.setServerTime(now());

        partyService.appendChat(partyId, incomingMessage);

        broadcast(partyId, new PartyMessage(
                PartyEvent.CHAT,
                incomingMessage,
                userId,
                now()));
    }

    // ─── Buffering ────────────────────────────────────────────────────────────

    @MessageMapping("/party/{partyId}/buffering")
    public void buffering(@DestinationVariable String partyId,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(headerAccessor);
        boolean shouldForcePause = partyService.markBuffering(partyId, userId);

        if (shouldForcePause) {
            broadcast(partyId, new PartyMessage(PartyEvent.FORCE_PAUSE, Map.of("reason", "participant_buffering"),
                    "system", now()));
        }
    }

    @MessageMapping("/party/{partyId}/buffering-complete")
    public void bufferingComplete(@DestinationVariable String partyId,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(headerAccessor);
        boolean shouldResume = partyService.markBufferingComplete(partyId, userId);

        if (shouldResume) {
            broadcast(partyId,
                    new PartyMessage(PartyEvent.RESUME, Map.of("reason", "all_buffering_resolved"), "system", now()));
        }
    }

    // ─── Strict-sync toggle (host only) ───────────────────────────────────────

    @MessageMapping("/party/{partyId}/toggle-strict-sync")
    public void toggleStrictSync(@DestinationVariable String partyId,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(headerAccessor);
        boolean newValue = partyService.toggleStrictSync(partyId, userId);

        broadcast(partyId, new PartyMessage(
                PartyEvent.STRICT_SYNC_TOGGLED,
                Map.of("strictSync", newValue),
                userId,
                now()));
    }

    // ─── WebSocket heartbeat (alternative to REST heartbeat) ─────────────────

    @MessageMapping("/party/{partyId}/heartbeat-ws")
    public void heartbeatWs(@DestinationVariable String partyId,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(headerAccessor);
        partyService.recordHeartbeat(partyId, userId);
        // Also run zombie eviction on every heartbeat tick
        partyService.evictZombies(partyId);

        // Reply only to the sender — use user-specific queue if needed; here we use a
        // simple broadcast
        messaging.convertAndSendToUser(userId, "/queue/heartbeat-ack",
                new PartyMessage(PartyEvent.HEARTBEAT_ACK, Map.of("ack", now()), "system", now()));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void broadcast(String partyId, PartyMessage message) {
        messaging.convertAndSend("/topic/party/" + partyId, message);
    }

    private long now() {
        return Instant.now().toEpochMilli();
    }

    private String resolveUserId(SimpMessageHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null && attrs.containsKey(WebSocketAuthInterceptor.SESSION_USER_ID)) {
            return (String) attrs.get(WebSocketAuthInterceptor.SESSION_USER_ID);
        }
        // Fallback to Spring Security principal name
        if (accessor.getUser() != null) {
            return accessor.getUser().getName();
        }
        throw new org.springframework.messaging.MessagingException("Unauthenticated WebSocket message");
    }
}
