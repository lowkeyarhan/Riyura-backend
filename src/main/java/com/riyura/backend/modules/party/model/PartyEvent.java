package com.riyura.backend.modules.party.model;

public enum PartyEvent {
    USER_JOINED,
    USER_LEFT,
    NEW_HOST_ASSIGNED,
    CHAT,
    SYNC,
    FORCE_PAUSE,
    RESUME,
    HEARTBEAT_ACK,
    PARTY_CLOSED,
    STRICT_SYNC_TOGGLED,
    ERROR
}
