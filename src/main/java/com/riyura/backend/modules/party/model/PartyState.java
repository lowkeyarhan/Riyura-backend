package com.riyura.backend.modules.party.model;

import com.riyura.backend.modules.party.dto.ChatMessage;
import lombok.Data;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

// This is the state of the party
// It is stored in the database and can be used to reconstruct the party state
// when the party is created

@Data
public class PartyState implements Serializable {

    private String partyId;
    private String hostId;
    private long tmdbId;
    private String mediaType;
    private int seasonNo;
    private int episodeNo;
    private String providerId;
    private int startAt;
    private long partyStartedAt;
    private boolean strictSync;
    private List<String> participantIds = new ArrayList<>();
    private List<String> bufferingParticipants = new ArrayList<>();
    private List<ChatMessage> recentChat = new ArrayList<>();
    private java.util.Map<String, Long> lastHeartbeat = new java.util.HashMap<>();
}
