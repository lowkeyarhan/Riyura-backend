package com.riyura.backend.modules.identity.service;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.modules.identity.dto.history.DeleteWatchHistoryRequest;
import com.riyura.backend.modules.identity.dto.history.HistoryResponse;
import com.riyura.backend.modules.identity.dto.history.TmdbMetadataDTO;
import com.riyura.backend.modules.identity.dto.history.HistoryRequest;
import com.riyura.backend.modules.identity.model.WatchHistory;
import com.riyura.backend.modules.identity.repository.WatchHistoryRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HistoryService {

    private final RestTemplate restTemplate;
    private final WatchHistoryRepository watchHistoryRepository;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    // Get user watch history
    @Cacheable(value = "history", key = "#userId", sync = true)
    public List<HistoryResponse> getUserWatchHistory(UUID userId) {
        return watchHistoryRepository.findByUserIdOrderByWatchedAtDesc(userId)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    // Add or update watch history
    @Transactional
    @CacheEvict(value = "history", key = "#userId")
    public WatchHistory addOrUpdateHistory(UUID userId, HistoryRequest request) {
        try {
            Optional<WatchHistory> existing = watchHistoryRepository.findByUserIdAndTmdbIdAndMediaType(
                    userId, request.getTmdbId(), request.getMediaType());

            WatchHistory history = existing.orElse(new WatchHistory());
            boolean sameContext = isSameContext(history, request, existing.isPresent());

            if (existing.isEmpty()) {
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

            history.setStreamId(request.getStreamId());
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
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save history", e);
        }
    }

    // Delete watch history
    @Transactional
    @CacheEvict(value = "history", key = "#userId")
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

    // Helper method to fetch metadata from TMDB
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

                String showUrl = String.format("%s/tv/%d?api_key=%s", baseUrl, id, apiKey);
                TmdbMetadataDTO showData = restTemplate.getForObject(showUrl, TmdbMetadataDTO.class);

                String episodeUrl = String.format("%s/tv/%d/season/%d/episode/%d?api_key=%s",
                        baseUrl, id, seasonNumber, episodeNumber, apiKey);
                TmdbMetadataDTO episodeData = restTemplate.getForObject(episodeUrl, TmdbMetadataDTO.class);

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

    // Helper method to check if the history is same as request
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

    // Helper method to apply metadata to WatchHistory
    private void applyMetadata(WatchHistory history, HistoryRequest request, TmdbMetadataDTO metadata) {
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

    // Helper method to parse date
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    // Helper method to convert WatchHistory to HistoryResponse
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

    // Helper method to check if the metadata is anime
    private boolean isAnime(TmdbMetadataDTO metadata) {
        return TmdbUtils.isAnime(metadata.getOriginalLanguage(), metadata.getGenres());
    }
}
