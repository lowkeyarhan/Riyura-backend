package com.riyura.backend.modules.watchalong.model.party;

import com.riyura.backend.modules.watchalong.model.enums.PartyEventType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// SSE event envelope published to Redis Pub/Sub and delivered to connected clients
// Payload shape per eventType:
// USER_JOINED -> { userId, username, avatarUrl }
// USER_LEFT -> { userId }
// USER_EVICTED -> { userId, reason }
// HOST_MIGRATED -> { newHostId, newHostName }
// PARTY_STATE_UPDATED -> { progress, providerId }
// NEW_CHAT -> { id, senderId, senderName, avatarUrl, content, sentAt }
// HEARTBEAT -> { userId }
// PARTY_ENDED -> { endedAt }

@Getter
@Setter
public class PartyEvent {

    private UUID eventId;
    // 8-char string party code (not UUID)
    private String partyId;
    private UUID triggeredById;
    private PartyEventType eventType;
    private Object payload;

    private Instant occurredAt;
}
