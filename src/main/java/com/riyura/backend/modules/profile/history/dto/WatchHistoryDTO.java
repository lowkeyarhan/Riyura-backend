package com.riyura.backend.modules.profile.history.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

// DTO for Watch History API response
// This represents the data we send back to the frontend for each watch history item, mapped from

@Data
public class WatchHistoryDTO {

    private Long id;

    @JsonProperty("user_id")
    private UUID userId;

    @JsonProperty("tmdb_id")
    private Long tmdbId;

    private String title;

    @JsonProperty("media_type")
    private String mediaType;

    @JsonProperty("stream_id")
    private String streamId;

    @JsonProperty("poster_path")
    private String posterPath;

    @JsonProperty("backdrop_path")
    private String backdropPath;

    @JsonProperty("release_date")
    private String releaseDate;

    @JsonProperty("duration_sec")
    private Integer durationSec;

    @JsonProperty("season_number")
    private Integer seasonNumber;

    @JsonProperty("episode_number")
    private Integer episodeNumber;

    @JsonProperty("episode_name")
    private String episodeName;

    @JsonProperty("episode_length")
    private Integer episodeLength;

    @JsonProperty("watched_at")
    private OffsetDateTime watchedAt;
}