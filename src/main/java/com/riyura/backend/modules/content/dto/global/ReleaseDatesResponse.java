package com.riyura.backend.modules.content.dto.global;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReleaseDatesResponse {

    private List<Result> results;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private String iso_3166_1;
        private List<ReleaseDate> release_dates;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReleaseDate {
        private String certification;
    }
}
