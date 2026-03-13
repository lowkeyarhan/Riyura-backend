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
    MediaType mediaType;
    String posterPath;
    Integer year;
    Integer seasons;
    Integer episodes;
    String genres;
    String reason;

    public static RecommendationsResponse from(Recommendation r) {
        return RecommendationsResponse.builder()
                .tmdbId(r.getTmdbId())
                .title(r.getTitle())
                .mediaType(r.getMediaType())
                .posterPath(r.getPosterPath())
                .year(r.getReleaseDate() != null ? r.getReleaseDate().getYear() : null)
                .seasons(r.getNumberOfSeasons())
                .episodes(r.getNumberOfEpisodes())
                .genres(r.getGenre())
                .reason(r.getReason())
                .build();
    }
}
