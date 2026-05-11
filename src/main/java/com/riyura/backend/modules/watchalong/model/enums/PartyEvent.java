package com.riyura.backend.modules.watchalong.model.enums;

// This enum represents the different types of events that can occur in a watch-along party.
public enum PartyEvent {
    USER_JOINED,
    USER_LEFT,
    USER_EVICTED,
    HOST_MIGRATED,
    PARTY_ENDED,
    NEW_CHAT,
    HEARTBEAT,
    PARTY_STATE_UPDATED,
    SYNC
}
