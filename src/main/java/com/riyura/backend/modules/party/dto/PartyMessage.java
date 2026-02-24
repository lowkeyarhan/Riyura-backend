package com.riyura.backend.modules.party.dto;

import com.riyura.backend.modules.party.model.PartyEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic STOMP broadcast envelope for all party events.
 * The frontend discriminates on {@code event} and casts {@code payload}
 * accordingly.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartyMessage {

    private PartyEvent event;

    /**
     * Can be a ChatMessage, SyncCommand, Map, or plain String depending on event
     * type.
     */
    private Object payload;

    /** Supabase UUID of the actor that triggered this event. */
    private String senderId;

    /** Server epoch ms — clients use this for late-joiner offset computation. */
    private long serverTime;
}
