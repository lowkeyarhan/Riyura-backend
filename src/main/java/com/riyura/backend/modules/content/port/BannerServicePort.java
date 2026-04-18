package com.riyura.backend.modules.content.port;

import com.riyura.backend.modules.content.dto.banner.BannerResponse;
import java.util.List;

public interface BannerServicePort {
    List<BannerResponse> getBannerData();
}
