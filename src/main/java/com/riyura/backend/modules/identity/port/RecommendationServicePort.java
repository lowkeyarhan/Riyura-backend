package com.riyura.backend.modules.identity.port;

import com.riyura.backend.modules.identity.model.Recommendation;
import java.util.List;
import java.util.UUID;

public interface RecommendationServicePort {
    List<Recommendation> getRecommendations(UUID userId, boolean forceRefresh);
}
