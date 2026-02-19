package com.riyura.backend.modules.identity.dto.history;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbMetadataDTO {
    private Long id;
    private String title;

    @JsonProperty("name")
    private String name;

    @JsonProperty("poster_path")
    private String posterPath;

    @JsonProperty("backdrop_path")
    private String backdropPath;

    @JsonProperty("release_date")
    private String releaseDate;

    @JsonProperty("air_date")
    private String airDate;

    @JsonProperty("first_air_date")
    private String firstAirDate;

    @JsonProperty("original_language")
    private String originalLanguage;

    private List<Genre> genres;

    @JsonProperty("runtime")
    private Integer runtime;

    @JsonProperty("episode_run_time")
    private List<Integer> episodeRunTime;

    @JsonProperty("season_number")
    private Integer seasonNumber;

    @JsonProperty("episode_number")
    private Integer episodeNumber;

    @JsonProperty("show_id")
    private Long showId;

    private String episodeName;

    public Integer resolveRuntime() {
        if (runtime != null) {
            return runtime;
        }
        if (episodeRunTime != null && !episodeRunTime.isEmpty()) {
            return episodeRunTime.get(0);
        }
        return null;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Genre {
        private Long id;
        private String name;
    }
}
