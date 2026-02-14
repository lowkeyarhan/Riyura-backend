package com.riyura.backend.modules.profile.history.service;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.riyura.backend.modules.profile.history.dto.WatchHistoryDTO;
import com.riyura.backend.modules.profile.history.model.WatchHistory;
import com.riyura.backend.modules.profile.history.repository.WatchHistoryRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HistoryService {

    private final WatchHistoryRepository watchHistoryRepository;

    // Get watch history for a user, ordered by most recent first
    public List<WatchHistoryDTO> getUserWatchHistory(UUID userId) {
        return watchHistoryRepository.findByUserIdOrderByWatchedAtDesc(userId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // Helper method to convert WatchHistory entity to DTO
    private WatchHistoryDTO mapToDTO(WatchHistory entity) {
        WatchHistoryDTO dto = new WatchHistoryDTO();
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