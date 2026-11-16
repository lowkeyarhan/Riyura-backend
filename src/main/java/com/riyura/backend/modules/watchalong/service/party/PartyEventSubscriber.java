package com.riyura.backend.modules.watchalong.service.party;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.riyura.backend.modules.watchalong.model.enums.PartyEventType;
import com.riyura.backend.modules.watchalong.model.party.PartyEvent;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

// Redis Pub/Sub (party:*:events) -> SSE fan-out.
@Slf4j
@Component
@RequiredArgsConstructor
public class PartyEventSubscriber implements MessageListener {

    private final SseEmitterRegistry sseEmitterRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            PartyEvent event = objectMapper.readValue(body, PartyEvent.class);

            String partyId = event.getPartyId();
            if (partyId == null || partyId.isBlank()) {
                log.warn(
                    "Received party event with null/blank partyId, skipping"
                );
                return;
            }

            sseEmitterRegistry.broadcast(
                partyId,
                event.getEventId().toString(),
                event.getEventType().name(),
                body
            );

            // Broadcast first, then close every stream.
            if (
                event.getEventType() == PartyEventType.PARTY_ENDED
            ) sseEmitterRegistry.completeAll(partyId);
        } catch (Exception e) {
            log.error("Failed to process party event from Redis Pub/Sub", e);
        }
    }
}
