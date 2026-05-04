package com.riyura.backend.modules.content.dto.movie;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class MoviePlayerResponse {
    private Long tmdbId;
    private String title;
    private List<String> genres;
    private String overview;

    @JsonProperty("backdrop_path")
    private String backdropPath;

    @JsonProperty("is_anime")
    private boolean anime;
}
