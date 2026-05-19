package com.riyura.backend.modules.watchalong.service.party;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.riyura.backend.modules.watchalong.model.enums.PartyEventType;
import com.riyura.backend.modules.watchalong.model.party.PartyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// Receives party events via Redis Pub/Sub and fans them out to connected SSE clients
// Subscribed to pattern: party:*:events
@Slf4j
@Component
@RequiredArgsConstructor
public class PartyEventSubscriber implements MessageListener {

    private final SseEmitterRegistry sseEmitterRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // Deserializes each Redis message and broadcasts it to the correct party's SSE
    // clients
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            PartyEvent event = objectMapper.readValue(body, PartyEvent.class);

            String partyId = event.getPartyId();
            if (partyId == null || partyId.isBlank()) {
                log.warn("Received party event with null/blank partyId, skipping");
                return;
            }

            SseEmitter.SseEventBuilder sseEvent = SseEmitter.event()
                    .id(event.getEventId().toString())
                    .name(event.getEventType().name())
                    .data(body);

            sseEmitterRegistry.broadcast(partyId, sseEvent);

            // On PARTY_ENDED, complete all connections after broadcasting the final event
            if (event.getEventType() == PartyEventType.PARTY_ENDED)
                sseEmitterRegistry.completeAll(partyId);

        } catch (Exception e) {
            log.error("Failed to process party event from Redis Pub/Sub", e);
        }
    }
}
