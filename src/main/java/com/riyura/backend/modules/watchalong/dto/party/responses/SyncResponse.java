package com.riyura.backend.modules.watchalong.dto.party.responses;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Response for GET /api/watchalong/party/{partyId}/sync — load streamUrl directly into the player
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SyncResponse {
    // Raw progress in seconds, for UI display only
    private double progress;
    private String providerId;
    // Stream URL built by the backend with startAt=progress embedded
    private String streamUrl;
}
