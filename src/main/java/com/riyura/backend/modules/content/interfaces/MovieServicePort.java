package com.riyura.backend.modules.content.interfaces;

import com.riyura.backend.common.dto.media.MediaGridResponse;
import java.util.List;

/**
 * Port interface for movie listing operations.
 * Controllers depend on this abstraction, not the concrete service (DIP).
 */
public interface MovieServicePort {
    List<MediaGridResponse> getNowPlayingMovies(int limit);

    List<MediaGridResponse> getTrendingMovies(int limit);

    List<MediaGridResponse> getPopularMovies(int limit);

    List<MediaGridResponse> getUpcomingMovies(int limit);
}
