package com.riyura.backend.modules.watchalong.interfaces;

import com.riyura.backend.modules.watchalong.dto.tv.TvPlayerResponse;

public interface TvPlayerServicePort {
    TvPlayerResponse getTvPlayer(String id);
}
