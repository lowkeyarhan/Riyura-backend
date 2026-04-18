package com.riyura.backend.modules.content.port;

import com.riyura.backend.modules.content.dto.tv.TvPlayerResponse;

public interface TvPlayerServicePort {
    TvPlayerResponse getTvPlayer(String id);
}
