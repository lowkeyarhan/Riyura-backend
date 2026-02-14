package com.riyura.backend.modules.profile.history.repository;

import com.riyura.backend.modules.profile.history.model.WatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {

    // Fetch by user ID and sort by most recently watched
    List<WatchHistory> findByUserIdOrderByWatchedAtDesc(UUID userId);
}