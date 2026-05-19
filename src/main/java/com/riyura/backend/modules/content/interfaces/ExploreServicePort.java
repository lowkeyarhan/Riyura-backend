package com.riyura.backend.modules.content.interfaces;

import com.riyura.backend.modules.content.dto.explore.ExploreResponse;
import java.util.List;

public interface ExploreServicePort {
    List<ExploreResponse> getExplorePage(int page, String genreNames, String language);
}
