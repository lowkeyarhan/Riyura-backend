package com.riyura.backend.modules.content.dto.global;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrewResponse {

    @JsonAlias({ "name", "original_name" })
    @JsonProperty("original_name")
    private String originalName;

    @JsonProperty("profile_path")
    private String profilePath;

    private String job;
}