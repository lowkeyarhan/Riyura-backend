package com.riyura.backend.modules.party.controller;

import com.riyura.backend.modules.party.dto.ChangeProviderCommand;
import com.riyura.backend.modules.party.dto.ChatMessage;
import com.riyura.backend.modules.party.dto.PartyMessage;
import com.riyura.backend.modules.party.dto.SyncCommand;
import com.riyura.backend.modules.party.model.PartyEvent;
import com.riyura.backend.modules.party.model.PartyState;
import com.riyura.backend.modules.party.security.WebSocketAuthInterceptor;
import com.riyura.backend.modules.party.port.PartyServicePort;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PartyWebSocketController {

    private final PartyServicePort partyService;
    private final SimpMessagingTemplate messaging;

    // Join a party and broadcast the updated participant list to all members
    @MessageMapping("/party/{partyId}/join")
    public void join(@DestinationVariable String partyId,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(headerAccessor);
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        String userName = sessionString(attrs, "userName", userId);

        PartyState state = partyService.addParticipant(partyId, userId, userName);

        // Store partyId in WebSocket session attributes for disconnect cleanup.
        if (attrs != null) {
            attrs.put("partyId", partyId);
        }

        // Broadcast the updated participant list to all members
        broadcast(partyId, new PartyMessage(
                PartyEvent.USER_JOINED,
                Map.of("userId", userId, "userName", userName, "participantIds", state.getParticipantIds()),
                userId,
                now()));

        // Auto-sync the joining participant's player to the host's current position
        messaging.convertAndSendToUser(headerAccessor.getUser().getName(), "/queue/sync",
                new PartyMessage(
                        PartyEvent.SYNC,
                        syncPayload(state, SyncCommand.Action.SEEK),
                        "system",
                        now()));
    }

    // Sync command from a client to update the party's playback position
    @MessageMapping("/party/{partyId}/sync")
    public void sync(@DestinationVariable String partyId,
            @Valid @Payload SyncCommand command,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(headerAccessor);
        SyncCommand.Action action = command != null ? command.getAction() : null;
        if (action == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sync action is required");
        }

        PartyState state = switch (action) {
            case SEEK, UPDATE, PLAY, PAUSE -> partyService.applySync(partyId, userId, command);
        };

        // Broadcast the new sync position to all members, along with the server time
        // for latency compensation
        broadcast(partyId, new PartyMessage(
                PartyEvent.SYNC,
                syncPayload(state, action),
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

        if (userId.equals(state.getHostId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "The request sync is only for participants, host cannot do it");
        }

        broadcast(partyId, new PartyMessage(
                PartyEvent.SYNC_REQUESTED,
                Map.of("requesterId", userId),
                "system",
                now()));

        // Send the current party position only to the requesting participant
        messaging.convertAndSendToUser(headerAccessor.getUser().getName(), "/queue/sync",
                new PartyMessage(
                        PartyEvent.SYNC,
                        syncPayload(state, SyncCommand.Action.SEEK),
                        "system",
                        now()));
    }

    // Host-triggered provider/server switch. The updated provider is persisted so
    // late joiners and request-sync responses receive the current server too.
    @MessageMapping("/party/{partyId}/change-provider")
    public void changeProvider(@DestinationVariable String partyId,
            @Valid @Payload ChangeProviderCommand command,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(headerAccessor);
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerId is required");
        }
        PartyState state = partyService.changeProvider(partyId, userId, command.getProviderId());

        broadcast(partyId, new PartyMessage(
                PartyEvent.PROVIDER_CHANGED,
                Map.of("providerId", state.getProviderId()),
                userId,
                now()));
    }

    // Chat message from a client to be appended to the party's chat history and
    // broadcast to all members
    @MessageMapping("/party/{partyId}/chat")
    public void chat(@DestinationVariable String partyId,
            @Valid @Payload ChatMessage incomingMessage,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(headerAccessor);
        if (incomingMessage == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat message is required");
        }
        incomingMessage.setSenderId(userId);

        // Retrieve optional display name and photo from session attributes set during
        // CONNECT
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        if (attrs != null) {
            if (attrs.containsKey("userName")) {
                incomingMessage.setSenderDisplayName(sessionString(attrs, "userName", userId));
            }
            if (attrs.containsKey("userPhoto")) {
                incomingMessage.setSenderProfilePhoto(sessionString(attrs, "userPhoto", ""));
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
        PartyState state = partyService.markBuffering(partyId, userId);

        if (state != null) {
            broadcast(partyId, new PartyMessage(PartyEvent.FORCE_PAUSE,
                    Map.of("startAt", state.getStartAt(), "reason", "participant_buffering"), "system", now()));
        }
    }

    // Mark a participant as having resolved their buffering; if all participants
    // are ready, the party can resume
    @MessageMapping("/party/{partyId}/buffering-complete")
    public void bufferingComplete(@DestinationVariable String partyId,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(headerAccessor);
        PartyState state = partyService.markBufferingComplete(partyId, userId);

        if (state != null) {
            broadcast(partyId,
                    new PartyMessage(PartyEvent.RESUME,
                            Map.of("startAt", state.getStartAt(), "reason", "all_buffering_resolved"), "system",
                            now()));
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
        messaging.convertAndSendToUser(headerAccessor.getUser().getName(), "/queue/heartbeat-ack",
                new PartyMessage(PartyEvent.HEARTBEAT_ACK, Map.of(), "system", now()));
    }

    @org.springframework.messaging.handler.annotation.MessageExceptionHandler
    public void handleException(Exception ex, SimpMessageHeaderAccessor headerAccessor) {
        try {
            String errorMessage = ex.getMessage();
            if (ex instanceof org.springframework.web.server.ResponseStatusException rse) {
                errorMessage = rse.getReason();
            } else {
                // For unexpected exceptions, log them. (We skip logging for
                // ResponseStatusException to keep terminal clean as requested)
                log.error("Unhandled websocket exception", ex);
            }
            messaging.convertAndSendToUser(headerAccessor.getUser().getName(), "/queue/error",
                    new PartyMessage(PartyEvent.ERROR,
                            Map.of("message", errorMessage != null ? errorMessage : "An error occurred"), "system",
                            now()));
        } catch (Exception ignored) {
            log.error("Failed to handle websocket exception", ex);
        }
    }

    // Helper method to broadcast a message to all members of a party
    private void broadcast(String partyId, PartyMessage message) {
        messaging.convertAndSend("/topic/party/" + partyId, message);
    }

    private Map<String, Object> syncPayload(PartyState state, SyncCommand.Action action) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("startAt", state.getStartAt());
        payload.put("partyStartedAt", state.getPartyStartedAt());
        payload.put("action", action.name());
        payload.put("providerId", state.getProviderId());
        return payload;
    }

    // Helper method to get the current server time in milliseconds
    private long now() {
        return Instant.now().toEpochMilli();
    }

    private String sessionString(Map<String, Object> attrs, String key, String fallback) {
        if (attrs == null) {
            return fallback;
        }
        Object value = attrs.get(key);
        if (value == null) {
            return fallback;
        }
        String stringValue = value.toString();
        return stringValue.isBlank() ? fallback : stringValue;
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
