package com.riyura.backend.modules.watchalong.model.party;

import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.watchalong.model.enums.PartyStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Core party state stored as a JSON blob in Redis at key: party:{partyId}
// partyId is an 8-char alphanumeric code — this is what users share to join
@Getter
@Setter
public class Party {

    @NotBlank(message = "Party ID cannot be blank")
    @Size(min = 8, max = 8, message = "Party ID must be exactly 8 characters")
    private String partyId;

    @NotNull(message = "Host user ID cannot be null")
    private java.util.UUID hostId;

    @NotNull(message = "Media type cannot be null")
    private MediaType mediaType;

    @NotBlank(message = "Provider ID cannot be blank")
    private String providerId;

    // StreamUrl is NOT stored — built on-demand at join/sync via StreamUrlService
    @Positive(message = "TMDB ID must be positive")
    private long tmdbId;

    @Min(value = 0, message = "Season number cannot be negative")
    private int seasonNo;

    @Min(value = 0, message = "Episode number cannot be negative")
    private int episodeNo;

    @NotNull(message = "Party status cannot be null")
    private PartyStatus status = PartyStatus.ACTIVE;

    // Raw playback position in seconds — starts at 0.0 on party creation
    @DecimalMin(value = "0.0", message = "Progress cannot be negative")
    private double progress = 0.0;

    @NotEmpty(message = "Participants list cannot be empty")
    @Valid
    private List<PartyParticipants> participants = new ArrayList<>();

    @NotNull(message = "Created-at timestamp cannot be null")
    private Instant createdAt;

    private Instant endedAt;
}
