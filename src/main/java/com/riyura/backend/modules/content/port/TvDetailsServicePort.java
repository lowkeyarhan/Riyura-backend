package com.riyura.backend.modules.content.port;

import com.riyura.backend.common.dto.media.MediaGridResponse;
import com.riyura.backend.modules.content.dto.tv.TvShowDetails;
import java.util.List;

public interface TvDetailsServicePort {
    TvShowDetails getTvDetails(String id);

    List<MediaGridResponse> getSimilarTvShows(String id);
}
