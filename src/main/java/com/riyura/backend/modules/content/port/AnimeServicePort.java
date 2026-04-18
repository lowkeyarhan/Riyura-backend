package com.riyura.backend.modules.content.port;

import com.riyura.backend.common.dto.media.MediaGridResponse;
import java.util.List;

public interface AnimeServicePort {
    List<MediaGridResponse> getTrendingAnime(int limit);
}
