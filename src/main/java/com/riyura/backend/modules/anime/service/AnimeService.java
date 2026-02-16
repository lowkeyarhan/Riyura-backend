package com.riyura.backend.modules.anime.service;

import com.riyura.backend.common.dto.MediaGridResponse;
import com.riyura.backend.common.dto.TmdbTrendingResponse;
import com.riyura.backend.common.model.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnimeService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    @Value("${tmdb.image-base-url}")
    private String imageBaseUrl;

    // Fetch Trending Anime (TV + Movies) - Combined & Sorted by Rating
    public List<MediaGridResponse> getTrendingAnime(int limit) {
        // Run fetches in parallel
        CompletableFuture<List<AnimeHelper>> tvTask = CompletableFuture.supplyAsync(this::fetchAnimeTv);
        CompletableFuture<List<AnimeHelper>> movieTask = CompletableFuture.supplyAsync(this::fetchAnimeMovies);

        // Wait for both
        List<AnimeHelper> tvResults = tvTask.join();
        List<AnimeHelper> movieResults = movieTask.join();

        // Combine
        List<AnimeHelper> allAnime = new ArrayList<>();
        allAnime.addAll(tvResults);
        allAnime.addAll(movieResults);

        // Sort by Vote Average (Descending) & Limit
        return allAnime.stream()
                .filter(item -> item.tmdbItem.getVoteAverage() != null)
                .sorted(Comparator.comparingDouble((AnimeHelper h) -> h.tmdbItem.getVoteAverage()).reversed())
                .limit(limit)
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // Fetch Anime TV Shows (Genre: Animation, Language: Japanese)
    private List<AnimeHelper> fetchAnimeTv() {
        // TMDB Discover API for TV shows with filters for Animation and Japanese
        String url = String.format(
                "%s/discover/tv?api_key=%s&sort_by=popularity.desc&vote_count.gte=50&with_genres=16&with_original_language=ja&page=1",
                baseUrl, apiKey);
        return fetchAndWrap(url, MediaType.TV);
    }

    // Fetch Anime Movies (Genre: Animation, Language: Japanese)
    private List<AnimeHelper> fetchAnimeMovies() {
        String url = String.format(
                "%s/discover/movie?api_key=%s&sort_by=popularity.desc&vote_count.gte=50&with_genres=16&with_original_language=ja&page=1",
                baseUrl, apiKey);
        return fetchAndWrap(url, MediaType.Movie);
    }

    // Common method to fetch data from TMDB and wrap in AnimeHelper
    private List<AnimeHelper> fetchAndWrap(String url, MediaType type) {
        try {
            TmdbTrendingResponse response = restTemplate.getForObject(url, TmdbTrendingResponse.class);
            if (response == null || response.getResults() == null) {
                return Collections.emptyList();
            }

            // Filter out items without poster and wrap results with their media type
            return response.getResults().stream()
                    .filter(item -> item.getPosterPath() != null && !item.getPosterPath().isEmpty())
                    .map(item -> new AnimeHelper(item, type))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Error fetching anime (" + type + "): " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // Map AnimeHelper to MediaGridResponse DTO
    private MediaGridResponse mapToDTO(AnimeHelper helper) {
        TmdbTrendingResponse.TmdbItem item = helper.tmdbItem;
        MediaGridResponse dto = new MediaGridResponse();

        dto.setTmdbId(item.getId());

        // Map Title/Name based on type
        if (helper.type == MediaType.Movie) {
            dto.setTitle(item.getTitle());
            dto.setYear(extractYear(item.getReleaseDate()));
        } else {
            dto.setTitle(item.getName());
            dto.setYear(extractYear(item.getFirstAirDate()));
        }

        if (item.getPosterPath() != null) {
            dto.setPosterUrl(imageBaseUrl + item.getPosterPath());
        }

        dto.setMediaType(helper.type);
        return dto;
    }

    // Extract year from date string safely
    private String extractYear(String dateString) {
        if (dateString == null || dateString.isEmpty())
            return null;
        try {
            return String.valueOf(LocalDate.parse(dateString).getYear());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // Helper record to keep track of MediaType before merging lists
    private record AnimeHelper(TmdbTrendingResponse.TmdbItem tmdbItem, MediaType type) {
    }
}