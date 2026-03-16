package com.riyura.backend.modules.identity.dto.recomendation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

// What Gemini returns in the RAG pipeline — just an ID from the candidate pool + a reason.
// The full metadata (poster, backdrop, title, etc.) is already held in the candidate pool
// and looked up by tmdb_id, so no title or type fields are needed here.
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiRecommendationItem {
    @JsonProperty("tmdb_id")
    private Long tmdbId;
    private String reason;
}