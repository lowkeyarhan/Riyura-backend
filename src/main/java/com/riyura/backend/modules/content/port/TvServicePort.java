package com.riyura.backend.modules.content.port;

import com.riyura.backend.common.dto.media.MediaGridResponse;
import java.util.List;

public interface TvServicePort {
    List<MediaGridResponse> getAiringToday(int limit);

    List<MediaGridResponse> getTrendingTv(int limit);

    List<MediaGridResponse> getPopularTv(int limit);

    List<MediaGridResponse> getOnTheAir(int limit);
}
