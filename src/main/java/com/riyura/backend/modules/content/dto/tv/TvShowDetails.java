package com.riyura.backend.modules.content.dto.tv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.riyura.backend.common.dto.CastResponse;
import com.riyura.backend.common.util.GenreLike;
import com.riyura.backend.modules.content.model.Season;

import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TvShowDetails {

    @JsonProperty("id")
    private Long tmdbId;

    @JsonProperty("name")
    private String title;

    private String overview;

    @JsonProperty("backdrop_path")
    private String backdropPath;

    private Long budget;

    private boolean adult;

    private List<Genre> genres;

    @JsonProperty("created_by")
    private List<CreatedBy> createdBy;

    private List<Network> networks;

    @JsonProperty("production_companies")
    private List<ProductionCompany> productionCompanies;

    @JsonProperty("first_air_date")
    private String firstAirDate;

    @JsonProperty("origin_country")
    private List<String> originCountry;

    @JsonProperty("original_language")
    private String originalLanguage;

    private Long revenue;
    private Integer runtime;
    private String status;
    private String tagline;
    private List<Season> seasons;

    @JsonProperty("vote_average")
    private Double voteAverage;

    private List<CastResponse> casts;

    @JsonProperty("episode_run_time")
    public void setEpisodeRunTime(List<Integer> episodeRunTime) {
        if (episodeRunTime == null || episodeRunTime.isEmpty()) {
            this.runtime = null;
            return;
        }
        this.runtime = episodeRunTime.get(0);
    }

    @JsonProperty("is_anime")
    private boolean anime;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Genre implements GenreLike {
        private Long id;
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreatedBy {
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Network {
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductionCompany {
        private String name;
    }
}
