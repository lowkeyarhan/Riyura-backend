package com.riyura.backend.modules.content.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Episode {

    @JsonProperty("episode_number")
    private int episodeNumber;

    private String name;
    private String overview;

    @JsonProperty("still_path")
    private String stillPath;

    @JsonProperty("air_date")
    private String airDate;
}
