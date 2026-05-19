package com.riyura.backend.modules.watchalong.interfaces;

import com.riyura.backend.modules.watchalong.dto.party.requests.CreatePartyRequest;
import com.riyura.backend.modules.watchalong.dto.party.requests.JoinPartyRequest;
import com.riyura.backend.modules.watchalong.dto.party.requests.PartyProgressRequest;
import com.riyura.backend.modules.watchalong.dto.party.requests.SendChatRequest;
import com.riyura.backend.modules.watchalong.dto.party.responses.PartyStateResponse;
import com.riyura.backend.modules.watchalong.dto.party.responses.SyncResponse;

import java.util.UUID;

public interface PartyServicePort {

    // Creates a new party with the caller as host; progress=0, streamUrl built
    // immediately for response
    PartyStateResponse createParty(CreatePartyRequest request, UUID userId, String username, String avatarUrl);

    // Adds the caller to an existing party by 8-char code; returns snapshot with
    // built streamUrl
    PartyStateResponse joinParty(JoinPartyRequest request, UUID userId, String username, String avatarUrl);

    // Removes the caller; triggers host migration or party end if no participants
    // remain
    void leaveParty(String partyId, UUID userId);

    // Host-only: stores raw progress + providerId; URL is built only on join/sync
    void pushProgress(PartyProgressRequest request, UUID callerId);

    // Participant liveness signal; must be sent every ~2.5 min to avoid zombie
    // eviction
    void sendHeartbeat(String partyId, UUID userId);

    // Stores a chat message in Redis and broadcasts NEW_CHAT event to all party SSE
    // connections
    void sendChat(SendChatRequest request, UUID senderId, String senderName, String avatarUrl);

    // Builds the current streamUrl from stored progress and returns it for
    // immediate player load
    SyncResponse sync(String partyId, UUID userId);

    // Returns full party metadata and recent messages; does not build a streamUrl
    PartyStateResponse getPartyState(String partyId, UUID userId);
}
