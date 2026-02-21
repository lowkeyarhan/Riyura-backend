package com.riyura.backend.modules.content.dto.tv;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.riyura.backend.modules.content.model.Season;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TvPlayerResponse {
    private Long tmdbId;
    private String title;
    private String overview;
    private List<String> genres;
    private List<Season> seasons;

    @JsonProperty("is_anime")
    private boolean anime;
}
