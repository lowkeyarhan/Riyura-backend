package com.riyura.backend.modules.watchalong.service.party;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.riyura.backend.modules.watchalong.model.enums.PartyEventType;
import com.riyura.backend.modules.watchalong.model.party.PartyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

// Publishes party events to Redis Pub/Sub channel: party:{partyId}:events
@Slf4j
@Component
@RequiredArgsConstructor
public class PartyEventPublisher {

    private final StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // Returns the Pub/Sub channel name for a given party
    public static String eventsChannel(String partyId) {
        return "party:" + partyId + ":events";
    }

    // Serializes the event envelope to JSON and publishes it to the party's Redis
    // channel
    public void publishEvent(String partyId, PartyEventType eventType, UUID triggeredById, Object payload) {
        PartyEvent event = new PartyEvent();
        event.setEventId(UUID.randomUUID());
        event.setPartyId(partyId);
        event.setTriggeredById(triggeredById);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setOccurredAt(Instant.now());

        try {
            stringRedisTemplate.convertAndSend(eventsChannel(partyId), objectMapper.writeValueAsString(event));
            log.debug("Published {} event for partyId={}", eventType, partyId);
        } catch (Exception e) {
            log.error("Failed to publish {} event for partyId={}", eventType, partyId, e);
        }
    }
}
