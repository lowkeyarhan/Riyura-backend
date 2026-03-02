package com.riyura.backend.modules.identity.dto.history;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.riyura.backend.common.model.MediaType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HistoryRequest {
    @NotNull(message = "tmdb_id is required")
    @Positive(message = "tmdb_id must be positive")
    @JsonProperty("tmdb_id")
    private Long tmdbId;

    @NotNull(message = "media_type is required")
    @JsonProperty("media_type")
    private MediaType mediaType;

    @Size(max = 255, message = "stream_id must not exceed 255 characters")
    @JsonProperty("stream_id")
    private String streamId;

    @NotBlank(message = "provider_id is required")
    @Size(max = 255, message = "provider_id must not exceed 255 characters")
    @JsonProperty("provider_id")
    private String providerId;

    @Min(value = 0, message = "duration_sec must not be negative")
    @JsonProperty("duration_sec")
    private Integer durationSec;

    @Positive(message = "season_number must be positive")
    @JsonProperty("season_number")
    private Integer seasonNumber;

    @Positive(message = "episode_number must be positive")
    @JsonProperty("episode_number")
    private Integer episodeNumber;

    @JsonIgnore
    @AssertTrue(message = "season_number and episode_number are required for TV history")
    public boolean isTvEpisodeValid() {
        if (mediaType != MediaType.TV) {
            return true;
        }
        return seasonNumber != null && episodeNumber != null;
    }
}
