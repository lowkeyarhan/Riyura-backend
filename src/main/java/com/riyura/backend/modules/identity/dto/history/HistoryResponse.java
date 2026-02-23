package com.riyura.backend.modules.identity.dto.history;

import com.riyura.backend.common.model.MediaType;
import lombok.Data;

// DTO for Watch History API response
// Exposes only the fields relevant to the frontend

@Data
public class HistoryResponse {

    private Long tmdbId;
    private String title;
    private String backdropPath;
    private MediaType mediaType;
    private String providerId;
    private Integer durationSec;
    private Integer episodeLength;
    private String episodeName;
    private Integer episodeNumber;
    private Integer seasonNumber;
    private Boolean isAnime;
    private Integer releaseYear;
}
