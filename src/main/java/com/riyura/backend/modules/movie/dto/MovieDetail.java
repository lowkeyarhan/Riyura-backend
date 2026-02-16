package com.riyura.backend.modules.movie.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.riyura.backend.common.dto.CastDetailsResponse;

import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MovieDetail {

    @JsonProperty("id")
    private Long tmdbId;

    private String title;
    private String overview;

    @JsonProperty("backdrop_path")
    private String backdropPath;

    private Long budget;
    private boolean adult;

    private List<Genre> genres;

    @JsonProperty("production_companies")
    private List<ProductionCompany> productionCompanies;

    @JsonProperty("release_date")
    private String releaseDate;

    private Long revenue;
    private Integer runtime;
    private String status;
    private String tagline;

    @JsonProperty("vote_average")
    private Double voteAverage;

    private List<CastDetailsResponse> casts;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Genre {
        private Long id;
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductionCompany {
        private Long id;
        private String name;
    }
}
