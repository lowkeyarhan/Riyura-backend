package com.riyura.backend.modules.content.dto.tv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.riyura.backend.modules.content.dto.global.ContentRatingsResponse;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbTvShowDetailsResponse extends TvShowDetails {

    @JsonProperty("content_ratings")
    private ContentRatingsResponse contentRatings;
}
