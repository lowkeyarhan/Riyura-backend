package com.riyura.backend.modules.content.dto.movie;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.riyura.backend.modules.content.dto.global.ReleaseDatesResponse;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbMovieDetailResponse extends MovieDetail {

    @JsonProperty("release_dates")
    private ReleaseDatesResponse releaseDates;
}
