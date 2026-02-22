package com.riyura.backend.common.dto.explore;

import com.riyura.backend.common.model.MediaType;
import lombok.Data;

@Data
public class ExploreDto {

    private Long tmdbId;
    private String title;
    private MediaType mediaType;
    private String releaseYear;
    private String originalLanguage;
    private Double rating;
    private String description;
    private String posterPath;
}
