package com.riyura.backend.modules.party.port;

import com.riyura.backend.modules.party.dto.ChatMessage;
import com.riyura.backend.modules.party.dto.PartyCreateRequest;
import com.riyura.backend.modules.party.dto.PartyStateResponse;
import com.riyura.backend.modules.party.dto.SyncCommand;
import com.riyura.backend.modules.party.model.PartyState;

public interface PartyServicePort {
    PartyState createParty(String hostId, PartyCreateRequest request);

    PartyStateResponse getState(String partyId);

    PartyState getPartyState(String partyId);

    PartyState addParticipant(String partyId, String userId, String userName);

    PartyState handleDisconnect(String partyId, String userId, String userName);

    void recordHeartbeat(String partyId, String userId);

    PartyState evictZombies(String partyId);

    PartyState applySync(String partyId, String userId, SyncCommand command);

    PartyState changeProvider(String partyId, String userId, String providerId);

    PartyState appendChat(String partyId, ChatMessage message);

    PartyState markBuffering(String partyId, String userId);

    PartyState markBufferingComplete(String partyId, String userId);

    boolean toggleStrictSync(String partyId, String hostId);
}
