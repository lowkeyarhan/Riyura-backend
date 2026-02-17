package com.riyura.backend.modules.identity.dto.history;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.riyura.backend.common.model.MediaType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WatchHistoryRequest {
    @NotNull(message = "tmdb_id is required")
    @JsonProperty("tmdb_id")
    private Long tmdbId;

    @NotNull(message = "media_type is required")
    @JsonProperty("media_type")
    private MediaType mediaType;

    @NotBlank(message = "stream_id is required")
    @JsonProperty("stream_id")
    private String streamId;

    @JsonProperty("duration_sec")
    private Integer durationSec;

    @JsonProperty("season_number")
    private Integer seasonNumber;

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
