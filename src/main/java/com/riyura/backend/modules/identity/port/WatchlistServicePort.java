package com.riyura.backend.modules.identity.port;

import com.riyura.backend.common.dto.media.MediaGridResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.identity.dto.watchlist.WatchlistRequest;
import com.riyura.backend.modules.identity.model.Watchlist;

import java.util.List;
import java.util.UUID;

public interface WatchlistServicePort {
    List<MediaGridResponse> getUserWatchlist(UUID userId, int page);

    boolean isInWatchlist(UUID userId, Long tmdbId, MediaType mediaType);

    Watchlist addToWatchlist(UUID userId, WatchlistRequest request);

    void deleteFromWatchlist(UUID userId, WatchlistRequest request);
}
