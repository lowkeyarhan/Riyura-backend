package com.riyura.backend.modules.party.event;

import com.riyura.backend.modules.party.dto.PartyMessage;
import com.riyura.backend.modules.party.model.PartyEvent;
import com.riyura.backend.modules.party.model.PartyState;
import com.riyura.backend.modules.party.security.WebSocketAuthInterceptor;
import com.riyura.backend.modules.party.service.PartyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final PartyService partyService;
    private final SimpMessagingTemplate messagingTemplate;

    // This is the event listener that is used to handle the disconnect event
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {

        // Get the session attributes
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null)
            return;

        // Get the user id and party id from the session attributes
        String userId = (String) sessionAttributes.get(WebSocketAuthInterceptor.SESSION_USER_ID);
        String partyId = (String) sessionAttributes.get("partyId");

        // If the user id or party id is null, return
        if (userId == null || partyId == null) {
            return;
        }

        log.info("Disconnect detected — user [{}] leaving party [{}]", userId, partyId);

        PartyState updated = partyService.handleDisconnect(partyId, userId);

        String topic = "/topic/party/" + partyId;

        if (updated == null) {
            // Party was destroyed
            messagingTemplate.convertAndSend(topic, new PartyMessage(
                    PartyEvent.PARTY_CLOSED,
                    Map.of("reason", "All participants left"),
                    userId,
                    Instant.now().toEpochMilli()));
            return;
        }

        // Broadcast USER_LEFT
        messagingTemplate.convertAndSend(topic, new PartyMessage(
                PartyEvent.USER_LEFT,
                Map.of("userId", userId, "participantIds", updated.getParticipantIds()),
                userId,
                Instant.now().toEpochMilli()));

        // If host migrated, notify with new host info
        if (!userId.equals(updated.getHostId())) {
        } else {
            return;
        }

        // Host migration occurred — broadcast
        messagingTemplate.convertAndSend(topic, new PartyMessage(
                PartyEvent.NEW_HOST_ASSIGNED,
                Map.of("newHostId", updated.getHostId()),
                "system",
                Instant.now().toEpochMilli()));
    }
}
