package com.riyura.backend.modules.identity.service;

import com.riyura.backend.common.dto.MediaGridResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.identity.dto.watchlist.WatchlistRequest;
import com.riyura.backend.modules.identity.model.Watchlist;
import com.riyura.backend.modules.identity.repository.WatchlistRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final RestTemplate restTemplate;
    private final WatchlistRepository watchlistRepository;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    @Value("${tmdb.image-base-url}")
    private String imageBaseUrl;

    // Fetches the user's watchlist, ordered by most recent first
    public List<MediaGridResponse> getUserWatchlist(UUID userId) {
        return watchlistRepository.findByUserIdOrderByAddedAtDesc(userId)
                .stream()
                .map(this::mapToMediaGridResponse)
                .collect(Collectors.toList());
    }

    // Adds a media item to the user's watchlist (or updates it if it already
    // exists)
    @Transactional
    public Watchlist addToWatchlist(UUID userId, WatchlistRequest request) {
        try {
            Optional<Watchlist> existing = watchlistRepository.findByUserIdAndTmdbIdAndMediaType(
                    userId, request.getTmdbId(), request.getMediaType());
            if (existing.isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Watchlist item already exists");
            }

            Watchlist watchlist = new Watchlist();
            watchlist.setUserId(userId);
            watchlist.setTmdbId(request.getTmdbId());
            watchlist.setMediaType(request.getMediaType());

            Map<String, Object> tmdbData = fetchTmdbMetadata(request.getTmdbId(), request.getMediaType());
            applyMetadata(watchlist, request.getMediaType(), tmdbData);
            watchlist.setAddedAt(OffsetDateTime.now());

            return watchlistRepository.save(watchlist);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save watchlist item", e);
        }
    }

    // Deletes a media item from the user's watchlist
    @Transactional
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

    // Helper methods to fetch metadata from TMDB and apply it to the Watchlist
    // entity
    private Map<String, Object> fetchTmdbMetadata(Long tmdbId, MediaType mediaType) {
        try {
            String endpoint = mediaType == MediaType.Movie ? "movie" : "tv";
            String url = String.format("%s/%s/%d?api_key=%s", baseUrl, endpoint, tmdbId, apiKey);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
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
    private void applyMetadata(Watchlist watchlist, MediaType mediaType, Map<String, Object> tmdbData) {
        String title = mediaType == MediaType.Movie
                ? readString(tmdbData.get("title"))
                : readString(tmdbData.get("name"));
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TMDB metadata is missing title");
        }

        String releaseDateValue = mediaType == MediaType.Movie
                ? readString(tmdbData.get("release_date"))
                : readString(tmdbData.get("first_air_date"));

        watchlist.setTitle(title);
        watchlist.setPosterPath(readString(tmdbData.get("poster_path")));
        watchlist.setReleaseDate(parseDate(releaseDateValue));
        watchlist.setVote(readBigDecimal(tmdbData.get("vote_average")));

        if (mediaType == MediaType.TV) {
            watchlist.setNumberOfSeasons(readInteger(tmdbData.get("number_of_seasons")));
            watchlist.setNumberOfEpisodes(readInteger(tmdbData.get("number_of_episodes")));
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
            response.setPosterUrl(posterPath.startsWith("http") ? posterPath : imageBaseUrl + posterPath);
        }

        return response;
    }

    // Helper methods to safely read values from the TMDB response and handle nulls
    // or type issues
    private String readString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    // Safely reads an integer value from the TMDB response, handling nulls and type
    // issues
    private Integer readInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Safely reads a BigDecimal value from the TMDB response, handling nulls and
    // type issues
    private BigDecimal readBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Parses a date string in ISO format
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }
}
