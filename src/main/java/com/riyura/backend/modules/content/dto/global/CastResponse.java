/* */
package com.riyura.backend.modules.content.dto.global;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CastResponse {

    @JsonProperty("original_name")
    private String originalName;

    @JsonProperty("profile_path")
    private String profilePath;

    private String character;

}