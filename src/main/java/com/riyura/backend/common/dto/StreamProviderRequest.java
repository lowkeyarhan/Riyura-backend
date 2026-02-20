package com.riyura.backend.common.dto;

import lombok.Data;

// What the backend receives from the frontend when requesting stream URLs for a media item.
// tmdbId is required to identify the media item and look up stream providers.
// seasonNo and episodeNo are required for TV shows to substitute into URL templates.
// startAt is optional — only sent when resuming from watch history.

@Data
public class StreamProviderRequest {
    private long tmdbId;
    private int seasonNo;
    private int episodeNo;
    private Integer startAt;
}
