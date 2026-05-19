package com.riyura.backend.modules.watchalong.dto.party.responses;

import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.watchalong.model.enums.PartyStatus;
import com.riyura.backend.modules.watchalong.model.party.Messages;
import com.riyura.backend.modules.watchalong.model.party.PartyParticipants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Full party snapshot returned on join and GET /party/{partyId}
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartyStateResponse {

    // The 8-char alphanumeric party code — this is what users share
    private String partyId;
    private UUID hostId;

    private MediaType mediaType;
    private long tmdbId;
    private int seasonNo;
    private int episodeNo;

    private String providerId;

    // Freshly built stream URL with startAt=progress embedded
    private String streamUrl;

    private double progress;
    private PartyStatus status;
    private Instant createdAt;

    private List<PartyParticipants> participants;

    // Last 50 messages for rendering chat history on join
    private List<Messages> recentMessages;
}
