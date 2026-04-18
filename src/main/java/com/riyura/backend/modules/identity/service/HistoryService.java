package com.riyura.backend.modules.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.riyura.backend.common.config.TmdbProperties;

import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.service.TmdbUrlBuilder;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.modules.identity.dto.history.DeleteWatchHistoryRequest;
import com.riyura.backend.modules.identity.dto.history.HistoryResponse;
import com.riyura.backend.modules.identity.dto.history.TmdbMetadataDTO;
import com.riyura.backend.modules.identity.dto.history.HistoryRequest;
import com.riyura.backend.modules.identity.model.WatchHistory;
import com.riyura.backend.modules.identity.repository.WatchHistoryRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryService implements com.riyura.backend.modules.identity.port.HistoryServicePort {

    private static final int MAX_HISTORY_SIZE = 1000;
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final RestTemplate restTemplate;
    private final WatchHistoryRepository watchHistoryRepository;
    private final TmdbProperties tmdbProperties;

    // Fetch the user's watch history with pagination
    // @Cacheable(value = "history", key = "#userId + ':' + #page", sync = true)
    @Transactional(readOnly = true)
    public List<HistoryResponse> getUserWatchHistory(UUID userId, int page) {
        Pageable pageable = PageRequest.of(page, DEFAULT_PAGE_SIZE);
        return watchHistoryRepository.findByUserIdOrderByWatchedAtDesc(userId, pageable)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    // Add or update a watch history item
    @Transactional
    // @CacheEvict(value = "history", allEntries = true)
    public WatchHistory addOrUpdateHistory(UUID userId, HistoryRequest request) {
        try {
            Optional<WatchHistory> existing = watchHistoryRepository.findByUserIdAndTmdbIdAndMediaType(
                    userId, request.getTmdbId(), request.getMediaType());

            WatchHistory history = existing.orElse(new WatchHistory());
            boolean sameContext = isSameContext(history, request, existing.isPresent());

            if (existing.isEmpty()) {
                // Enforce the per-user size limit
                long count = watchHistoryRepository.countByUserId(userId);
                if (count >= MAX_HISTORY_SIZE) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Watch history limit reached (" + MAX_HISTORY_SIZE + " items)");
                }

                history.setUserId(userId);
                history.setTmdbId(request.getTmdbId());
                history.setMediaType(request.getMediaType());
                sameContext = false;
            }

            TmdbMetadataDTO metadata = null;
            if (existing.isEmpty() || (request.getMediaType() == MediaType.TV && !sameContext)) {
                metadata = fetchMetadataFromTmdb(request.getTmdbId(), request.getMediaType(),
                        request.getSeasonNumber(), request.getEpisodeNumber());
                applyMetadata(history, request, metadata);
            }

            history.setProviderId(request.getProviderId());
            history.setDurationSec(request.getDurationSec());
            history.setWatchedAt(OffsetDateTime.now());

            if (metadata != null) {
                history.setIsAnime(isAnime(metadata));
            } else if (history.getIsAnime() == null) {
                history.setIsAnime(false);
            }

            return watchHistoryRepository.save(history);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Watch history entry already exists");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save history", e);
        }
    }

    // Delete a watch history item
    @Transactional
    // @CacheEvict(value = "history", allEntries = true)
    public void deleteWatchHistory(UUID userId, DeleteWatchHistoryRequest request) {
        try {
            Optional<WatchHistory> existing = watchHistoryRepository.findByUserIdAndTmdbIdAndMediaType(
                    userId, request.getTmdbId(), request.getMediaType());

            WatchHistory history = existing.orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Watch history entry not found"));
            watchHistoryRepository.delete(history);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete history", e);
        }
    }

    // Fetch metadata from TMDB
    private TmdbMetadataDTO fetchMetadataFromTmdb(Long id, MediaType type, Integer seasonNumber,
            Integer episodeNumber) {
        try {
            if (type == MediaType.Movie) {
                String url = TmdbUrlBuilder.from(tmdbProperties)
                        .path("/movie/" + id)
                        .build();
                return restTemplate.getForObject(url, TmdbMetadataDTO.class);
            } else {
                if (seasonNumber == null || episodeNumber == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "season_number and episode_number are required for TV history");
                }

                String showUrl = TmdbUrlBuilder.from(tmdbProperties)
                        .path("/tv/" + id)
                        .build();
                String episodeUrl = TmdbUrlBuilder.from(tmdbProperties)
                        .path("/tv/" + id + "/season/" + seasonNumber + "/episode/" + episodeNumber)
                        .build();

                CompletableFuture<TmdbMetadataDTO> showFuture = CompletableFuture
                        .supplyAsync(() -> restTemplate.getForObject(showUrl, TmdbMetadataDTO.class));
                CompletableFuture<TmdbMetadataDTO> episodeFuture = CompletableFuture
                        .supplyAsync(() -> restTemplate.getForObject(episodeUrl, TmdbMetadataDTO.class));

                TmdbMetadataDTO showData = showFuture.orTimeout(8, TimeUnit.SECONDS).join();
                TmdbMetadataDTO episodeData = episodeFuture.orTimeout(8, TimeUnit.SECONDS).join();

                if (showData == null || episodeData == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to fetch TV metadata from TMDB");
                }

                episodeData.setTitle(showData.getName());
                episodeData.setPosterPath(showData.getPosterPath());
                episodeData.setBackdropPath(showData.getBackdropPath());
                episodeData.setReleaseDate(showData.getFirstAirDate());
                episodeData.setOriginalLanguage(showData.getOriginalLanguage());
                episodeData.setGenres(showData.getGenres());
                episodeData.setShowId(id);
                episodeData.setEpisodeName(episodeData.getName());
                if (episodeData.getRuntime() == null) {
                    episodeData.setRuntime(showData.resolveRuntime());
                }
                return episodeData;
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch metadata from TMDB", e);
        }
    }

    // Check if the history is the same as the request
    private boolean isSameContext(WatchHistory history, HistoryRequest request, boolean existing) {
        if (!existing) {
            return false;
        }
        if (request.getMediaType() == MediaType.Movie) {
            return true;
        }
        return Objects.equals(history.getSeasonNumber(), request.getSeasonNumber())
                && Objects.equals(history.getEpisodeNumber(), request.getEpisodeNumber());
    }

    // Apply metadata to the watch history
    private void applyMetadata(WatchHistory history, HistoryRequest request, TmdbMetadataDTO metadata) {
        if (metadata == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to fetch metadata from TMDB");
        }

        String backdropPath = metadata.getBackdropPath();
        if ((backdropPath == null || backdropPath.isBlank())
                && metadata.getPosterPath() != null
                && !metadata.getPosterPath().isBlank()) {
            backdropPath = metadata.getPosterPath();
        }
        history.setBackdropPath(backdropPath);
        if (request.getMediaType() == MediaType.Movie) {
            history.setTitle(metadata.getTitle());
            history.setReleaseDate(parseDate(metadata.getReleaseDate()));
            history.setSeasonNumber(null);
            history.setEpisodeNumber(null);
            history.setEpisodeName(null);
        } else {
            history.setTitle(metadata.getTitle());
            history.setReleaseDate(parseDate(
                    metadata.getAirDate() != null ? metadata.getAirDate() : metadata.getReleaseDate()));
            history.setSeasonNumber(request.getSeasonNumber());
            history.setEpisodeNumber(request.getEpisodeNumber());
            history.setEpisodeName(metadata.getEpisodeName() != null ? metadata.getEpisodeName() : metadata.getName());
        }

        Integer runtimeMinutes = metadata.resolveRuntime();
        history.setEpisodeLength(runtimeMinutes != null ? runtimeMinutes * 60 : null);
    }

    // Parses a date string safely, returning null on invalid formats
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse date: {}", value);
            return null;
        }
    }

    // Convert the watch history to a history response
    private HistoryResponse toHistoryResponse(WatchHistory history) {
        HistoryResponse dto = new HistoryResponse();
        dto.setTmdbId(history.getTmdbId());
        dto.setTitle(history.getTitle());
        dto.setBackdropPath(history.getBackdropPath());
        dto.setMediaType(history.getMediaType());
        dto.setProviderId(history.getProviderId());
        dto.setDurationSec(history.getDurationSec());
        dto.setEpisodeLength(history.getEpisodeLength());
        dto.setEpisodeName(history.getEpisodeName());
        dto.setEpisodeNumber(history.getEpisodeNumber());
        dto.setSeasonNumber(history.getSeasonNumber());
        dto.setIsAnime(history.getIsAnime());
        dto.setReleaseYear(history.getReleaseDate() != null ? history.getReleaseDate().getYear() : null);
        return dto;
    }

    // Check if the metadata is anime
    private boolean isAnime(TmdbMetadataDTO metadata) {
        return TmdbUtils.isAnime(metadata.getOriginalLanguage(), metadata.getGenres());
    }
}
