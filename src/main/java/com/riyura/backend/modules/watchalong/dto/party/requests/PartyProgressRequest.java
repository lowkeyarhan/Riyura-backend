package com.riyura.backend.modules.watchalong.dto.party.requests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// Request body for POST /api/watchalong/party/progress — host-only, sent periodically
@Getter
@Setter
public class PartyProgressRequest {

    @NotBlank(message = "Party code is required")
    @Size(min = 8, max = 8, message = "Party code must be exactly 8 characters")
    private String partyId;

    // Raw playback position in seconds; backend builds streamUrl from this on
    // join/sync
    @DecimalMin(value = "0.0", message = "Progress cannot be negative")
    private double progress;

    // Only the host can change the provider; participant attempts are rejected with
    // 403
    @NotBlank(message = "Provider ID is required")
    private String providerId;
}
