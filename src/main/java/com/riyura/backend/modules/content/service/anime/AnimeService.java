package com.riyura.backend.modules.content.service.anime;

import com.riyura.backend.common.dto.MediaGridResponse;
import com.riyura.backend.common.dto.TmdbTrendingResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.util.TmdbUtils;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class AnimeService {

    private final RestTemplate restTemplate;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    @Value("${tmdb.image-base-url}")
    private String imageBaseUrl;

    // Fetches trending anime and returns a list of MediaGridResponse DTOs
    public List<MediaGridResponse> getTrendingAnime(int limit) {
        CompletableFuture<List<AnimeHelper>> tvTask = CompletableFuture.supplyAsync(this::fetchAnimeTv);
        CompletableFuture<List<AnimeHelper>> movieTask = CompletableFuture.supplyAsync(this::fetchAnimeMovies);

        List<AnimeHelper> allAnime = new ArrayList<>(tvTask.join());
        allAnime.addAll(movieTask.join());

        return allAnime.stream()
                .filter(item -> item.tmdbItem.getVoteAverage() != null)
                .sorted(Comparator.comparingDouble((AnimeHelper h) -> h.tmdbItem.getVoteAverage()).reversed())
                .limit(limit)
                .map(this::mapToDTO)
                .toList();
    }

    // Fetches trending anime TV shows from TMDb
    private List<AnimeHelper> fetchAnimeTv() {
        String url = String.format(
                "%s/discover/tv?api_key=%s&sort_by=popularity.desc&vote_count.gte=50&with_genres=16&with_original_language=ja&page=1",
                baseUrl, apiKey);
        return fetchAndWrap(url, MediaType.TV);
    }

    // Fetches anime movies from TMDb
    private List<AnimeHelper> fetchAnimeMovies() {
        String url = String.format(
                "%s/discover/movie?api_key=%s&sort_by=popularity.desc&vote_count.gte=50&with_genres=16&with_original_language=ja&page=1",
                baseUrl, apiKey);
        return fetchAndWrap(url, MediaType.Movie);
    }

    // Helper method to fetch data from TMDb and wrap it in AnimeHelper objects
    private List<AnimeHelper> fetchAndWrap(String url, MediaType type) {
        try {
            TmdbTrendingResponse response = restTemplate.getForObject(url, TmdbTrendingResponse.class);
            if (response == null || response.getResults() == null)
                return Collections.emptyList();
            return response.getResults().stream()
                    .filter(item -> item.getPosterPath() != null && !item.getPosterPath().isEmpty())
                    .map(item -> new AnimeHelper(item, type))
                    .toList();
        } catch (Exception e) {
            System.err.println("Error fetching anime (" + type + "): " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // Maps an AnimeHelper object to a MediaGridResponse DTO
    private MediaGridResponse mapToDTO(AnimeHelper helper) {
        TmdbTrendingResponse.TmdbItem item = helper.tmdbItem;
        MediaGridResponse dto = new MediaGridResponse();
        dto.setTmdbId(item.getId());

        if (helper.type == MediaType.Movie) {
            dto.setTitle(item.getTitle());
            dto.setYear(TmdbUtils.extractYear(item.getReleaseDate()));
        } else {
            dto.setTitle(item.getName());
            dto.setYear(TmdbUtils.extractYear(item.getFirstAirDate()));
        }

        if (item.getPosterPath() != null)
            dto.setPosterUrl(imageBaseUrl + item.getPosterPath());
        dto.setMediaType(helper.type);
        return dto;
    }

    // Helper record to hold TMDb item and its media type for easier processing
    private record AnimeHelper(TmdbTrendingResponse.TmdbItem tmdbItem, MediaType type) {
    }
}