package com.riyura.backend.modules.content.dto.movie;

import java.util.List;

import com.riyura.backend.common.dto.StreamUrlResponse;

import lombok.Data;

@Data
public class MoviePlayerResponse {
    private Long tmdbId;
    private String title;
    private List<String> genres;
    private List<StreamUrlResponse> streamUrls;
    private String overview;
}
