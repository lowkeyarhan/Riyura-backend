package com.riyura.backend.modules.content.dto.explore;

import com.riyura.backend.common.model.MediaType;
import lombok.Data;

@Data
public class ExploreResponse {

    private Long tmdbId;
    private String title;
    private MediaType mediaType;
    private String releaseYear;
    private String originalLanguage;
    private Double rating;
    private String description;
    private String posterPath;
}
