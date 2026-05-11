package com.riyura.backend.modules.watchalong.model.party;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

// The schema for the participants in a watch-along party, which will be stored as part of the Party class
// This class contains information about each participant, such as their user ID, username, avatar URL, and the time they joined the party.
@Getter
@Setter
public class PartyParticipants {
    private UUID userId;
    private String username;
    private String avatarUrl;
    private String joinedAt;
    private Long lastHeartbeat;
}
