package com.riyura.backend.modules.content.dto.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.riyura.backend.common.model.MediaType;
import lombok.Data;

// DTO for Search API response
// This represents the data we send back to the frontend for each search result item

@Data
public class SearchResponse {
    private Long tmdbId;
    private String title;

    @JsonProperty("media_type")
    private MediaType mediaType;

    @JsonProperty("release_year")
    private String releaseYear;

    @JsonProperty("original_language")
    private String originalLanguage;

    @JsonProperty("poster_path")
    private String posterPath;

    private String description;
}