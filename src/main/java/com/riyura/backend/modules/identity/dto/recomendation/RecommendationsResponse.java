package com.riyura.backend.modules.identity.dto.recomendation;

import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.identity.model.Recommendation;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RecommendationsResponse {

    Long tmdbId;
    String title;
    Integer year;
    MediaType mediaType;
    String reason;
    String posterPath;

    public static RecommendationsResponse from(Recommendation r, String imageBaseUrl) {
        String fullPosterPath = null;
        if (r.getPosterPath() != null) {
            fullPosterPath = r.getPosterPath().startsWith("http") ? r.getPosterPath()
                    : imageBaseUrl + r.getPosterPath();
        }

        return RecommendationsResponse.builder()
                .tmdbId(r.getTmdbId())
                .title(r.getTitle())
                .year(r.getReleaseDate() != null ? r.getReleaseDate().getYear() : null)
                .mediaType(r.getMediaType())
                .reason(r.getReason())
                .posterPath(fullPosterPath)
                .build();
    }
}
