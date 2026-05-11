package com.riyura.backend.modules.watchalong.model.party;

import java.util.UUID;

import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.watchalong.model.enums.PartyStatus;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// The main party state that will be stored in the database and used for all party-related operations
// This class contains all the necessary information about a watch-along party, including the host, media being watched, participants, and their progress.
@Getter
@Setter
public class Party {

    @NotBlank(message = "Party ID cannot be blank")
    private UUID partyId;

    @NotBlank(message = "Host ID cannot be blank")
    private UUID hostId;

    @NotBlank(message = "Media ID cannot be blank")
    private MediaType mediaType;

    @NotBlank(message = "Provider ID cannot be blank")
    private String providerId;

    @NotBlank(message = "TMDb ID cannot be blank")
    private long tmdbId;

    @NotBlank(message = "Party status cannot be blank")
    private PartyStatus status = PartyStatus.ACTIVE;

    @NotBlank(message = "Watch progress cannot be blank")
    private double progress;
    private int seasonNo;
    private int episodeNo;

    @NotBlank(message = "Participants list cannot be blank, host needs to be added as a participant")
    private List<PartyParticipants> participants;
}
