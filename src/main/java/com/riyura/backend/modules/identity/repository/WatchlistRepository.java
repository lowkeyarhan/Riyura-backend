package com.riyura.backend.modules.identity.repository;

import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.identity.model.Watchlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    // Find all watchlist items for a user, ordered by the date they were added
    // (most recent first)
    List<Watchlist> findByUserIdOrderByAddedAtDesc(UUID userId);

    // Find a specific watchlist item by user ID, TMDB ID, and media type
    Optional<Watchlist> findByUserIdAndTmdbIdAndMediaType(UUID userId, Long tmdbId, MediaType mediaType);
}
