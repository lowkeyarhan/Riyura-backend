package com.riyura.backend.modules.banner.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.riyura.backend.common.model.MediaType;
import lombok.Data;
import java.util.List;

// DTO for Banner API response
// This represents the data we send back to the frontend for each banner item, mapped from TMDB data

@Data
public class BannerResponse {

    private Long id;
    private Long tmdbId;
    private String title;
    private String overview;

    @JsonProperty("backdrop_path")
    private String backdropUrl;

    @JsonProperty("poster_path")
    private String posterUrl;

    @JsonProperty("vote_average")
    private Double rating;

    @JsonProperty("contentType")
    private MediaType mediaType;

    @JsonProperty("genres")
    private List<String> genres;

    @JsonProperty("adult")
    private boolean adult;

    private String maturityRating;
    private String year;
}