package com.riyura.backend.modules.party.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// What the frontend sends when someone is creating a party

@Data
public class PartyCreateRequest {

    @NotNull
    private Long tmdbId;

    @NotBlank
    private String mediaType;

    private int seasonNo;

    private int episodeNo;

    @NotBlank
    private String providerId;

    private Integer startAt = 0;
}
