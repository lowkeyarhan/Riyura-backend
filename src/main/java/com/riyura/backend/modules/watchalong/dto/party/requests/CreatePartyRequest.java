package com.riyura.backend.modules.watchalong.dto.party.requests;

import com.riyura.backend.common.model.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

// Request body for POST /api/watchalong/party/create
@Getter
@Setter
public class CreatePartyRequest {

    @NotNull(message = "Media type is required")
    private MediaType mediaType;

    @Positive(message = "TMDB ID must be positive")
    private long tmdbId;

    @NotBlank(message = "Provider ID is required")
    private String providerId;

    // Required only when mediaType is TV/Anime; 0 is valid for movies
    private int seasonNo;
    private int episodeNo;
}
