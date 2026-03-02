package com.riyura.backend.modules.identity.repository;

import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.identity.model.WatchHistory;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {

    // Fetch by user ID and sort by most recently watched, with pagination
    List<WatchHistory> findByUserIdOrderByWatchedAtDesc(UUID userId, Pageable pageable);

    // Find existing watch history by user, tmdb ID, and media type
    Optional<WatchHistory> findByUserIdAndTmdbIdAndMediaType(UUID userId, Long tmdbId, MediaType mediaType);

    // Count watch history items for a user
    long countByUserId(UUID userId);
}
