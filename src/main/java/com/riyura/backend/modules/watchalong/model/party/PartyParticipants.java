package com.riyura.backend.modules.watchalong.model.party;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// Single participant record stored as part of the Party JSON blob
@Getter
@Setter
public class PartyParticipants {

    @NotNull(message = "User ID cannot be null")
    private UUID userId;

    @NotBlank(message = "Username cannot be blank")
    private String username;

    private String avatarUrl;

    private boolean isHost;

    @NotNull(message = "Joined-at timestamp cannot be null")
    private Instant joinedAt;

    // Updated every heartbeat; zombie sweeper evicts participant if stale > 5
    // minutes
    @NotNull(message = "Last-heartbeat timestamp cannot be null")
    private Instant lastHeartbeat;
}
