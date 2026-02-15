package com.riyura.backend.modules.profile.history.service;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.profile.history.dto.DeleteWatchHistoryRequest;
import com.riyura.backend.modules.profile.history.dto.TmdbMetadataDTO;
import com.riyura.backend.modules.profile.history.dto.WatchHistoryRequest;
import com.riyura.backend.modules.profile.history.dto.WatchHistoryResponse;
import com.riyura.backend.modules.profile.history.model.WatchHistory;
import com.riyura.backend.modules.profile.history.repository.WatchHistoryRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HistoryService {

    private final RestTemplate restTemplate;
    private final WatchHistoryRepository watchHistoryRepository;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    // Fetches the user's watch history, ordered by most recent first
    public List<WatchHistoryResponse> getUserWatchHistory(UUID userId) {
        return watchHistoryRepository.findByUserIdOrderByWatchedAtDesc(userId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // Adds a new watch history entry or updates an existing one
    @Transactional
    public WatchHistoryResponse addOrUpdateHistory(UUID userId, WatchHistoryRequest request) {
        try {
            // Check if there's an existing history entry for the same TMDB ID and type
            Optional<WatchHistory> existing = watchHistoryRepository.findByUserIdAndTmdbIdAndMediaType(
                    userId, request.getTmdbId(), request.getMediaType());

            // For TV shows, we consider it the same context if season and episode match
            WatchHistory history = existing.orElse(new WatchHistory());
            boolean sameContext = isSameContext(history, request, existing.isPresent());
            int requestedDuration = request.getDurationSec() != null ? request.getDurationSec() : 0;
            int finalDuration = requestedDuration;

            // If it's the same context, we can accumulate the duration
            if (existing.isPresent() && sameContext) {
                finalDuration += history.getDurationSec() != null ? history.getDurationSec() : 0;
            }

            // If it's a different context, we should fetch metadata again
            if (existing.isEmpty()) {
                history.setUserId(userId);
                history.setTmdbId(request.getTmdbId());
                history.setMediaType(request.getMediaType());
                sameContext = false;
            }

            // For new entries or context changes, fetch metadata from TMDB
            TmdbMetadataDTO metadata = null;
            if (existing.isEmpty() || (request.getMediaType() == MediaType.TV && !sameContext)) {
                metadata = fetchMetadataFromTmdb(request.getTmdbId(), request.getMediaType(),
                        request.getSeasonNumber(), request.getEpisodeNumber());
                applyMetadata(history, request, metadata);
            }

            // Update stream and duration for both new and existing entries
            history.setStreamId(request.getStreamId());
            history.setDurationSec(finalDuration);
            history.setWatchedAt(OffsetDateTime.now());

            // Save the history entry and return the response DTO
            return mapToDTO(watchHistoryRepository.save(history));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save history", e);
        }
    }

    // Deletes a watch history entry based on the provided TMDB ID and context
    @Transactional
    public void deleteWatchHistory(UUID userId, DeleteWatchHistoryRequest request) {
        try {
            // Find the existing history entry for the given TMDB ID, media type, and user
            Optional<WatchHistory> existing = watchHistoryRepository.findByUserIdAndTmdbIdAndMediaType(
                    userId, request.getTmdbId(), request.getMediaType());

            // For TV shows, we need to ensure we match the correct season and episode
            WatchHistory history = existing.orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Watch history entry not found"));
            // For TV shows, we should only delete if the season and episode match the
            // request context
            watchHistoryRepository.delete(history);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete history", e);
        }
    }

    // Helper method to fetch metadata from TMDB based on media type and context
    private TmdbMetadataDTO fetchMetadataFromTmdb(Long id, MediaType type, Integer seasonNumber,
            Integer episodeNumber) {
        try {
            if (type == MediaType.Movie) {
                String url = String.format("%s/movie/%d?api_key=%s", baseUrl, id, apiKey);
                return restTemplate.getForObject(url, TmdbMetadataDTO.class);
            } else {
                if (seasonNumber == null || episodeNumber == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "season_number and episode_number are required for TV history");
                }

                // First fetch the show metadata to get the title and poster, then fetch the
                // episode metadata for specific details
                String showUrl = String.format("%s/tv/%d?api_key=%s", baseUrl, id, apiKey);
                TmdbMetadataDTO showData = restTemplate.getForObject(showUrl, TmdbMetadataDTO.class);

                // Then fetch episode details
                String episodeUrl = String.format("%s/tv/%d/season/%d/episode/%d?api_key=%s",
                        baseUrl, id, seasonNumber, episodeNumber, apiKey);
                TmdbMetadataDTO episodeData = restTemplate.getForObject(episodeUrl, TmdbMetadataDTO.class);

                // If we can't fetch either the show or episode data, we should throw an error
                if (showData == null || episodeData == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to fetch TV metadata from TMDB");
                }

                // Populate episodeData with show-level metadata for title and poster if not
                // present
                episodeData.setTitle(showData.getName());
                episodeData.setPosterPath(showData.getPosterPath());
                episodeData.setBackdropPath(showData.getBackdropPath());
                episodeData.setReleaseDate(showData.getFirstAirDate());
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

    // Determines if the existing history entry is for the same context (same movie
    // or same TV episode)
    private boolean isSameContext(WatchHistory history, WatchHistoryRequest request, boolean existing) {
        if (!existing) {
            return false;
        }
        if (request.getMediaType() == MediaType.Movie) {
            return true;
        }
        return Objects.equals(history.getSeasonNumber(), request.getSeasonNumber())
                && Objects.equals(history.getEpisodeNumber(), request.getEpisodeNumber());
    }

    // Applies metadata from TMDB to the WatchHistory entity based on the media type
    // and context
    private void applyMetadata(WatchHistory history, WatchHistoryRequest request, TmdbMetadataDTO metadata) {
        if (metadata == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to fetch metadata from TMDB");
        }

        history.setPosterPath(metadata.getPosterPath());
        history.setBackdropPath(metadata.getBackdropPath());
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

    // Parses a date string in ISO format
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    // Maps a WatchHistory entity to a WatchHistoryResponse DTO
    private WatchHistoryResponse mapToDTO(WatchHistory entity) {
        WatchHistoryResponse dto = new WatchHistoryResponse();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setTmdbId(entity.getTmdbId());
        dto.setTitle(entity.getTitle());
        dto.setMediaType(entity.getMediaType());
        dto.setStreamId(entity.getStreamId());
        dto.setPosterPath(entity.getPosterPath());
        dto.setBackdropPath(entity.getBackdropPath());

        if (entity.getReleaseDate() != null) {
            dto.setReleaseDate(entity.getReleaseDate().toString());
        }

        dto.setDurationSec(entity.getDurationSec());
        dto.setSeasonNumber(entity.getSeasonNumber());
        dto.setEpisodeNumber(entity.getEpisodeNumber());
        dto.setEpisodeName(entity.getEpisodeName());
        dto.setEpisodeLength(entity.getEpisodeLength());
        dto.setWatchedAt(entity.getWatchedAt());

        return dto;
    }

}
