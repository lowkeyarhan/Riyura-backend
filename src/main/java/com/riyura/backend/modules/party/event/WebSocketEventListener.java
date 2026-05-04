package com.riyura.backend.modules.party.event;

import com.riyura.backend.modules.party.security.WebSocketAuthInterceptor;
import com.riyura.backend.modules.party.service.PartyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final PartyService partyService;

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
        partyService.handleDisconnect(partyId, userId);
    }
}
