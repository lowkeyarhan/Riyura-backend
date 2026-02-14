package com.riyura.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.riyura.backend.modules.movie.model.MediaType;

import lombok.Data;

@Data
public class MediaGridItemDTO {

    private Long tmdbId;
    private String title;

    @JsonProperty("poster_path")
    private String posterUrl;

    @JsonProperty("year")
    private String year;

    @JsonProperty("media_type")
    private MediaType mediaType;
}