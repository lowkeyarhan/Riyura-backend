package com.riyura.backend.modules.watchalong.model.party;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

// This class represents an event that occurs within a watch-along party, such as a user joining, leaving, or sending a chat message.
@Getter
@Setter
public class PartyEvent {
    private UUID eventId;
    private UUID partyId;
    private UUID triggeredById;
    private PartyEvent eventType;
    private String description;
}
