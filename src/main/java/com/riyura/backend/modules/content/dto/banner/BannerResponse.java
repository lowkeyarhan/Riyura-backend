package com.riyura.backend.modules.content.dto.banner;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.riyura.backend.common.model.MediaType;
import lombok.Data;
import java.util.List;

// DTO for Banner API response
// This represents the data we send back to the frontend for each banner item, mapped from TMDB data

@Data
public class BannerResponse {

    private Long tmdbId;
    private String title;
    private String overview;

    @JsonProperty("backdrop_path")
    private String backdropUrl;

    @JsonProperty("contentType")
    private MediaType mediaType;

    @JsonProperty("genres")
    private List<String> genres;

    @JsonIgnore
    private boolean adult;

    private String maturityRating;
    private String year;
}