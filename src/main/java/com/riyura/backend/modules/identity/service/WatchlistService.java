package com.riyura.backend.modules.identity.service;

import com.riyura.backend.common.dto.media.MediaGridResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.identity.dto.history.TmdbMetadataDTO;
import com.riyura.backend.modules.identity.dto.watchlist.WatchlistRequest;
import com.riyura.backend.modules.identity.model.Watchlist;
import com.riyura.backend.modules.identity.repository.WatchlistRepository;
import com.riyura.backend.modules.identity.port.WatchlistServicePort;
import com.riyura.backend.common.config.TmdbProperties;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.common.service.TmdbUrlBuilder;
import com.riyura.backend.common.util.TmdbUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistService implements WatchlistServicePort {

    private static final int MAX_WATCHLIST_SIZE = 500;
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final WatchlistRepository watchlistRepository;
    private final TmdbClient tmdbClient;
    private final TmdbProperties tmdbProperties;

    // Fetches the user's watchlist, ordered by most recent first
    @Cacheable(value = "watchlist", key = "#userId + ':' + #page", sync = true)
    @Transactional(readOnly = true)
    public List<MediaGridResponse> getUserWatchlist(UUID userId, int page) {
        Pageable pageable = PageRequest.of(page, DEFAULT_PAGE_SIZE);
        return watchlistRepository.findByUserIdOrderByAddedAtDesc(userId, pageable)
                .stream()
                .map(this::mapToMediaGridResponse)
                .collect(Collectors.toList());
    }

    // Checks if a specific media item is in the user's watchlist
    @Transactional(readOnly = true)
    public boolean isInWatchlist(UUID userId, Long tmdbId, MediaType mediaType) {
        return watchlistRepository.findByUserIdAndTmdbIdAndMediaType(userId, tmdbId, mediaType).isPresent();
    }

    // Adds a media item to the user's watchlist
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "watchlist", key = "#userId + ':0'"),
            @CacheEvict(value = "watchlist", key = "#userId + ':1'")
    })
    public Watchlist addToWatchlist(UUID userId, WatchlistRequest request) {
        try {
            // Check if already exists
            if (watchlistRepository.findByUserIdAndTmdbIdAndMediaType(
                    userId, request.getTmdbId(), request.getMediaType()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Watchlist item already exists");
            }

            // Enforce per-user size limit
            long count = watchlistRepository.countByUserId(userId);
            if (count >= MAX_WATCHLIST_SIZE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Watchlist limit reached (" + MAX_WATCHLIST_SIZE + " items)");
            }

            Watchlist watchlist = new Watchlist();
            watchlist.setUserId(userId);
            watchlist.setTmdbId(request.getTmdbId());
            watchlist.setMediaType(request.getMediaType());

            TmdbMetadataDTO metadata = fetchTmdbMetadata(request.getTmdbId(), request.getMediaType());
            applyMetadata(watchlist, request.getMediaType(), metadata);
            watchlist.setAddedAt(OffsetDateTime.now());

            return watchlistRepository.save(watchlist);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Watchlist item already exists");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save watchlist item", e);
        }
    }

    // Deletes a media item from the user's watchlist
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "watchlist", key = "#userId + ':0'"),
            @CacheEvict(value = "watchlist", key = "#userId + ':1'")
    })
    public void deleteFromWatchlist(UUID userId, WatchlistRequest request) {
        try {
            Watchlist watchlist = watchlistRepository.findByUserIdAndTmdbIdAndMediaType(
                    userId, request.getTmdbId(), request.getMediaType())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Watchlist item not found"));

            watchlistRepository.delete(watchlist);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete watchlist item", e);
        }
    }

    // Fetches metadata from TMDB using typed DTO
    private TmdbMetadataDTO fetchTmdbMetadata(Long tmdbId, MediaType mediaType) {
        try {
            String endpoint = mediaType == MediaType.Movie ? "movie" : "tv";
            String url = TmdbUrlBuilder.from(tmdbProperties)
                    .path("/" + endpoint + "/" + tmdbId)
                    .build();

            TmdbMetadataDTO response = tmdbClient.fetchWithRetry(url, TmdbMetadataDTO.class);
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to fetch metadata from TMDB");
            }
            return response;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch metadata from TMDB", e);
        }
    }

    // Applies TMDB metadata to the Watchlist entity
    private void applyMetadata(Watchlist watchlist, MediaType mediaType, TmdbMetadataDTO metadata) {
        String title = mediaType == MediaType.Movie ? metadata.getTitle() : metadata.getName();
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TMDB metadata is missing title");
        }

        String releaseDateValue = mediaType == MediaType.Movie
                ? metadata.getReleaseDate()
                : metadata.getFirstAirDate();

        watchlist.setTitle(title);
        watchlist.setPosterPath(metadata.getPosterPath());
        watchlist.setReleaseDate(TmdbUtils.parseDate(releaseDateValue));
        watchlist.setVote(metadata.getVoteAverage() != null
                ? BigDecimal.valueOf(metadata.getVoteAverage())
                : null);

        if (mediaType == MediaType.TV) {
            watchlist.setNumberOfSeasons(metadata.getNumberOfSeasons());
            watchlist.setNumberOfEpisodes(metadata.getNumberOfEpisodes());
        } else {
            watchlist.setNumberOfSeasons(null);
            watchlist.setNumberOfEpisodes(null);
        }
    }

    // Helper method to map Watchlist entity to MediaGridResponse DTO
    private MediaGridResponse mapToMediaGridResponse(Watchlist watchlist) {
        MediaGridResponse response = new MediaGridResponse();
        response.setTmdbId(watchlist.getTmdbId());
        response.setTitle(watchlist.getTitle());
        response.setYear(
                watchlist.getReleaseDate() != null ? String.valueOf(watchlist.getReleaseDate().getYear()) : null);
        response.setMediaType(watchlist.getMediaType());

        String posterPath = watchlist.getPosterPath();
        if (posterPath != null && !posterPath.isBlank()) {
            response.setPosterUrl(
                    posterPath.startsWith("http") ? posterPath : tmdbProperties.imageBaseUrl() + posterPath);
        }

        return response;
    }
}
