package com.riyura.backend.modules.watchlist.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.riyura.backend.common.model.MediaType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WatchlistRequest {

    @NotNull(message = "tmdb_id is required")
    @JsonProperty("tmdb_id")
    private Long tmdbId;

    @NotNull(message = "media_type is required")
    @JsonProperty("media_type")
    private MediaType mediaType;
}
