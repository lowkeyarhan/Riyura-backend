package com.riyura.backend.modules.content.dto.stream;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;

// What the backend receives from the frontend when requesting stream URLs for a media item

@Data
public class StreamProviderRequest {
    @Positive(message = "tmdbId must be positive")
    private long tmdbId;

    @Min(value = 0, message = "seasonNo must not be negative")
    private int seasonNo;

    @Min(value = 0, message = "episodeNo must not be negative")
    private int episodeNo;

    @Min(value = 0, message = "startAt must not be negative")
    private Integer startAt;
}
