package com.riyura.backend.modules.watchalong.dto.party;

import java.util.UUID;

import com.riyura.backend.common.model.MediaType;
import java.util.List;
import com.riyura.backend.modules.watchalong.model.PartyParticipants;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Party {

    @NotBlank(message = "Party ID cannot be blank")
    private String partyId;

    @NotBlank(message = "Host ID cannot be blank")
    private UUID hostId;

    @NotBlank(message = "Media ID cannot be blank")
    private MediaType mediaType;

    @NotBlank(message = "Provider ID cannot be blank")
    private String providerId;

    @NotBlank(message = "TMDb ID cannot be blank")
    private long tmdbId;

    @NotBlank(message = "Watch progress cannot be blank")
    private double progress;
    private int seasonNo;
    private int episodeNo;

    @NotBlank(message = "Participants list cannot be blank, host needs to be added as a participant")
    private List<PartyParticipants> participants;
}
