package com.riyura.backend.modules.party.port;

import com.riyura.backend.modules.party.dto.ChatMessage;
import com.riyura.backend.modules.party.dto.PartyCreateRequest;
import com.riyura.backend.modules.party.dto.PartyStateResponse;
import com.riyura.backend.modules.party.model.PartyState;

public interface PartyServicePort {
    PartyState createParty(String hostId, PartyCreateRequest request);

    PartyStateResponse getState(String partyId);

    PartyState getPartyState(String partyId);

    PartyState addParticipant(String partyId, String userId);

    PartyState handleDisconnect(String partyId, String userId);

    void recordHeartbeat(String partyId, String userId);

    PartyState evictZombies(String partyId);

    PartyState applySeek(String partyId, String hostId, int startAt, long clientTime);

    PartyState appendChat(String partyId, ChatMessage message);

    boolean markBuffering(String partyId, String userId);

    boolean markBufferingComplete(String partyId, String userId);

    boolean toggleStrictSync(String partyId, String hostId);
}
