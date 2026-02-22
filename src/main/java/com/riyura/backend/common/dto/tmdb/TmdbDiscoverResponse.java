package com.riyura.backend.common.dto.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbDiscoverResponse {

    private int page;
    private List<TmdbDiscoverItem> results;

    @JsonProperty("total_pages")
    private int totalPages;

    @JsonProperty("total_results")
    private int totalResults;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TmdbDiscoverItem {

        private Long id;
        private String overview;

        @JsonProperty("poster_path")
        private String posterPath;

        @JsonProperty("vote_average")
        private Double voteAverage;

        @JsonProperty("original_language")
        private String originalLanguage;

        // Movie fields
        private String title;

        @JsonProperty("release_date")
        private String releaseDate;

        // TV fields
        private String name;

        @JsonProperty("first_air_date")
        private String firstAirDate;
    }
}
