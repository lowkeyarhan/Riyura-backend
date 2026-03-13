package com.riyura.backend.modules.identity.dto.recomendation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.riyura.backend.common.model.MediaType;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiRecommendationItem {
    private String title;
    private MediaType type; // Expected: "movie", "tv"
    private String reason;
    private String genre;
}