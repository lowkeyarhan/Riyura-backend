package com.riyura.backend.modules.watchalong.dto.party.requests;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

// What the frontend sends to the backend every 30 seconds to indicate that the user is still active in the party
//  and to update their last heartbeat timestamp in the database
@Getter
@Setter
public class HeartbeatRequest {
    private UUID partyId;
    private UUID userId;
}
