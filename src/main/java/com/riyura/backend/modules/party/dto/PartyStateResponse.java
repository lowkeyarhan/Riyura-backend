package com.riyura.backend.modules.party.dto;

import com.riyura.backend.modules.party.model.PartyState;
import lombok.Data;

import java.util.List;

/**
 * REST response for GET /api/party/{id}/state.
 * Includes serverTime so late joiners can compute the current playback offset.
 */
@Data
public class PartyStateResponse {

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
    private List<String> participantIds;
    private List<ChatMessage> recentChat;
    private long serverTime;

    public static PartyStateResponse from(PartyState state) {
        PartyStateResponse res = new PartyStateResponse();
        res.setPartyId(state.getPartyId());
        res.setHostId(state.getHostId());
        res.setTmdbId(state.getTmdbId());
        res.setMediaType(state.getMediaType());
        res.setSeasonNo(state.getSeasonNo());
        res.setEpisodeNo(state.getEpisodeNo());
        res.setProviderId(state.getProviderId());
        res.setStartAt(state.getStartAt());
        res.setPartyStartedAt(state.getPartyStartedAt());
        res.setStrictSync(state.isStrictSync());
        res.setParticipantIds(state.getParticipantIds());
        res.setRecentChat(state.getRecentChat());
        res.setServerTime(System.currentTimeMillis());
        return res;
    }
}
