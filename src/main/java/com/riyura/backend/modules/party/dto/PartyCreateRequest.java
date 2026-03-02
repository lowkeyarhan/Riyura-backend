package com.riyura.backend.modules.party.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

// What the frontend sends when someone is creating a party

@Data
public class PartyCreateRequest {

    @NotNull
    @Positive(message = "tmdbId must be positive")
    private Long tmdbId;

    @NotBlank
    @Pattern(regexp = "^(Movie|TV)$", message = "mediaType must be 'Movie' or 'TV'")
    private String mediaType;

    @Min(value = 0, message = "seasonNo must not be negative")
    private int seasonNo;

    @Min(value = 0, message = "episodeNo must not be negative")
    private int episodeNo;

    @NotBlank
    @Size(max = 255, message = "providerId must not exceed 255 characters")
    private String providerId;

    @Min(value = 0, message = "startAt must not be negative")
    private Integer startAt = 0;
}
