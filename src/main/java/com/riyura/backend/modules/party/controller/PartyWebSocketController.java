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

@Slf4j
@Controller
@RequiredArgsConstructor
public class PartyWebSocketController {

    private final PartyService partyService;
    private final SimpMessagingTemplate messaging;

    // Join a party and broadcast the updated participant list to all members
    @MessageMapping("/party/{partyId}/join")
    public void join(@DestinationVariable String partyId,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(headerAccessor);
        PartyState state = partyService.addParticipant(partyId, userId);

        // Store partyId in WebSocket session attributes for later use
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        String userName = userId;
        if (attrs != null) {
            attrs.put("partyId", partyId);
            if (attrs.containsKey("userName")) {
                userName = (String) attrs.get("userName");
            }
        }

        // Broadcast the updated participant list to all members
        broadcast(partyId, new PartyMessage(
                PartyEvent.USER_JOINED,
                Map.of("userId", userId, "userName", userName, "participantIds", state.getParticipantIds()),
                userId,
                now()));

        // Auto-sync the joining participant's player to the host's current position
        messaging.convertAndSendToUser(userId, "/queue/sync",
                new PartyMessage(
                        PartyEvent.SYNC,
                        Map.of("startAt", state.getStartAt(), "partyStartedAt", state.getPartyStartedAt()),
                        "system",
                        now()));
    }

    // Sync command from a client to update the party's playback position
    @MessageMapping("/party/{partyId}/sync")
    public void sync(@DestinationVariable String partyId,
            @Payload SyncCommand command,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(headerAccessor);
        PartyState state = partyService.applySeek(partyId, userId, command.getStartAt(), command.getClientTime());

        // Broadcast the new sync position to all members, along with the server time
        // for latency compensation
        broadcast(partyId, new PartyMessage(
                PartyEvent.SYNC,
                Map.of(
                        "startAt", state.getStartAt(),
                        "partyStartedAt", state.getPartyStartedAt()),
                userId,
                now()));
    }

    // Participant-triggered sync: sends the current host position back to just
    // the requesting user (failsafe "sync me to host" button)
    @MessageMapping("/party/{partyId}/request-sync")
    public void requestSync(@DestinationVariable String partyId,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(headerAccessor);
        PartyState state = partyService.getPartyState(partyId);

        // Send the current party position only to the requesting participant
        messaging.convertAndSendToUser(userId, "/queue/sync",
                new PartyMessage(
                        PartyEvent.SYNC,
                        Map.of("startAt", state.getStartAt(), "partyStartedAt", state.getPartyStartedAt()),
                        "system",
                        now()));
    }

    // Chat message from a client to be appended to the party's chat history and
    // broadcast to all members
    @MessageMapping("/party/{partyId}/chat")
    public void chat(@DestinationVariable String partyId,
            @Payload ChatMessage incomingMessage,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(headerAccessor);
        incomingMessage.setSenderId(userId);

        // Retrieve optional display name and photo from session attributes set during CONNECT
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        if (attrs != null) {
            if (attrs.containsKey("userName")) {
                incomingMessage.setSenderDisplayName((String) attrs.get("userName"));
            }
            if (attrs.containsKey("userPhoto")) {
                incomingMessage.setSenderProfilePhoto((String) attrs.get("userPhoto"));
            }
        }

        incomingMessage.setServerTime(now());

        partyService.appendChat(partyId, incomingMessage);

        broadcast(partyId, new PartyMessage(
                PartyEvent.CHAT,
                incomingMessage,
                userId,
                now()));
    }

    // Mark a participant as buffering; if enough participants are buffering, the
    // party will be forced to pause
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

    // Mark a participant as having resolved their buffering; if all participants
    // are ready, the party can resume
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

    // Toggle strict sync mode (only host can toggle) — when enabled, any new seek
    // command from a participant will be overridden by the host's current position
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

    // Heartbeat from a client to keep the party alive and trigger zombie eviction
    @MessageMapping("/party/{partyId}/heartbeat-ws")
    public void heartbeatWs(@DestinationVariable String partyId,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(headerAccessor);
        partyService.recordHeartbeat(partyId, userId);
        // Also run zombie eviction on every heartbeat tick
        partyService.evictZombies(partyId);

        // Send an ack back to the specific user to confirm the heartbeat was received
        messaging.convertAndSendToUser(userId, "/queue/heartbeat-ack",
                new PartyMessage(PartyEvent.HEARTBEAT_ACK, Map.of("ack", now()), "system", now()));
    }

    // Helper method to broadcast a message to all members of a party
    private void broadcast(String partyId, PartyMessage message) {
        messaging.convertAndSend("/topic/party/" + partyId, message);
    }

    // Helper method to get the current server time in milliseconds
    private long now() {
        return Instant.now().toEpochMilli();
    }

    // Helper method to resolve the user ID from the WebSocket session attributes or
    // Spring Security principal
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
