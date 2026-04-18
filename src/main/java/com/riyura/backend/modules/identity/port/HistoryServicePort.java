package com.riyura.backend.modules.identity.port;

import com.riyura.backend.modules.identity.dto.history.DeleteWatchHistoryRequest;
import com.riyura.backend.modules.identity.dto.history.HistoryRequest;
import com.riyura.backend.modules.identity.dto.history.HistoryResponse;
import com.riyura.backend.modules.identity.model.WatchHistory;

import java.util.List;
import java.util.UUID;

public interface HistoryServicePort {
    List<HistoryResponse> getUserWatchHistory(UUID userId, int page);

    WatchHistory addOrUpdateHistory(UUID userId, HistoryRequest request);

    void deleteWatchHistory(UUID userId, DeleteWatchHistoryRequest request);
}
