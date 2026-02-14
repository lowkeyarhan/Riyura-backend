package com.riyura.backend.modules.banner.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

// DTO for TMDB Trending API response
// This represemts how will the TMDB api response for trending movies/tv, later mapped to BannerModel

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbTrendingDTO {

    private int page;
    private List<TmdbItem> results;

    @JsonProperty("total_pages")
    private int totalPages;

    @JsonProperty("total_results")
    private int totalResults;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TmdbItem {
        private Long id;
        private String overview;

        @JsonProperty("backdrop_path")
        private String backdropPath;

        @JsonProperty("poster_path")
        private String posterPath;

        @JsonProperty("genre_ids")
        private List<Integer> genreIds;

        @JsonProperty("vote_average")
        private Double voteAverage;

        @JsonProperty("vote_count")
        private Integer voteCount;

        @JsonProperty("original_language")
        private String originalLanguage;

        private Boolean adult;
        private String title;

        @JsonProperty("original_title")
        private String originalTitle;

        @JsonProperty("release_date")
        private String releaseDate;

        private String name;

        @JsonProperty("original_name")
        private String originalName;

        @JsonProperty("first_air_date")
        private String firstAirDate;

        @JsonProperty("media_type")
        private String mediaType;
    }
}