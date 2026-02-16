package com.riyura.backend.modules.tv.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.riyura.backend.common.dto.StreamUrlResponse;
import com.riyura.backend.modules.tv.dto.TvShowDetails.Season;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TvPlayerResponse {
    private Long tmdbId;
    private String title;
    private String overview;
    private List<String> genres;
    private List<Season> seasons;
    private List<StreamUrlResponse> streamUrls;
}
