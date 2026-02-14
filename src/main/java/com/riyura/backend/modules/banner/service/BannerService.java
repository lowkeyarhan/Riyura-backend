package com.riyura.backend.modules.banner.service;

import com.riyura.backend.modules.banner.dto.BannerResponse;
import com.riyura.backend.modules.banner.dto.TmdbTrendingDTO;
import com.riyura.backend.modules.banner.model.UIMediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BannerService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    @Value("${tmdb.image-base-url}")
    private String imageBaseUrl;

    // Static Map of TMDB Genre IDs to Names
    private static final Map<Integer, String> GENRE_MAP = new HashMap<>();
    static {
        // --- Movie Genres ---
        GENRE_MAP.put(28, "Action");
        GENRE_MAP.put(12, "Adventure");
        GENRE_MAP.put(16, "Animation");
        GENRE_MAP.put(35, "Comedy");
        GENRE_MAP.put(80, "Crime");
        GENRE_MAP.put(99, "Documentary");
        GENRE_MAP.put(18, "Drama");
        GENRE_MAP.put(10751, "Family");
        GENRE_MAP.put(14, "Fantasy");
        GENRE_MAP.put(36, "History");
        GENRE_MAP.put(27, "Horror");
        GENRE_MAP.put(10402, "Music");
        GENRE_MAP.put(9648, "Mystery");
        GENRE_MAP.put(10749, "Romance");
        GENRE_MAP.put(878, "Science Fiction");
        GENRE_MAP.put(10770, "TV Movie");
        GENRE_MAP.put(53, "Thriller");
        GENRE_MAP.put(10752, "War");
        GENRE_MAP.put(37, "Western");

        // --- TV Genres ---
        // (Note: Some IDs like 16, 35, etc. overlap with movies, which is fine)
        GENRE_MAP.put(10759, "Action & Adventure");
        GENRE_MAP.put(10762, "Kids");
        GENRE_MAP.put(10763, "News");
        GENRE_MAP.put(10764, "Reality");
        GENRE_MAP.put(10765, "Sci-Fi & Fantasy");
        GENRE_MAP.put(10766, "Soap");
        GENRE_MAP.put(10767, "Talk");
        GENRE_MAP.put(10768, "War & Politics");
    }

    // Get the banner data for both movies and tv
    public List<BannerResponse> getBannerData() {
        CompletableFuture<List<BannerResponse>> moviesTask = CompletableFuture.supplyAsync(this::fetchTopMovies);
        CompletableFuture<List<BannerResponse>> tvTask = CompletableFuture.supplyAsync(this::fetchTopTV);

        List<BannerResponse> movies = moviesTask.join();
        List<BannerResponse> tvShows = tvTask.join();

        List<BannerResponse> allItems = new ArrayList<>();
        allItems.addAll(movies);
        allItems.addAll(tvShows);

        Collections.shuffle(allItems);

        return allItems;
    }

    // Fetch top 3 trending movies and map to BannerResponse
    private List<BannerResponse> fetchTopMovies() {
        String url = String.format("%s/trending/movie/week?api_key=%s", baseUrl, apiKey);
        return fetchAndMap(url, UIMediaType.movie);
    }

    // Fetch top 3 trending TV shows and map to BannerResponse
    private List<BannerResponse> fetchTopTV() {
        String url = String.format("%s/trending/tv/week?api_key=%s", baseUrl, apiKey);
        return fetchAndMap(url, UIMediaType.tv);
    }

    // Common method to fetch data from TMDB and map to BannerResponse
    private List<BannerResponse> fetchAndMap(String url, UIMediaType type) {
        try {
            TmdbTrendingDTO response = restTemplate.getForObject(url, TmdbTrendingDTO.class);
            if (response == null || response.getResults() == null) {
                return Collections.emptyList();
            }
            return response.getResults().stream()
                    .limit(3)
                    .map(item -> mapItemToBanner(item, type))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Error fetching banner data: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // Map a TMDB item to our BannerResponse model
    private BannerResponse mapItemToBanner(TmdbTrendingDTO.TmdbItem item, UIMediaType type) {
        BannerResponse model = new BannerResponse();

        model.setId(item.getId());
        model.setTmdbId(item.getId());

        if (type == UIMediaType.movie) {
            model.setTitle(item.getTitle());
            model.setYear(extractYear(item.getReleaseDate()));
        } else {
            model.setTitle(item.getName());
            model.setYear(extractYear(item.getFirstAirDate()));
        }

        model.setOverview(item.getOverview() != null ? item.getOverview() : "");
        model.setRating(item.getVoteAverage() != null ? item.getVoteAverage() : 0.0);
        model.setMediaType(type.name());

        if (item.getBackdropPath() != null)
            model.setBackdropUrl(imageBaseUrl + item.getBackdropPath());
        if (item.getPosterPath() != null)
            model.setPosterUrl(imageBaseUrl + item.getPosterPath());

        // Get IDs only for mapping purposes
        List<Integer> ids = item.getGenreIds() != null ? item.getGenreIds() : new ArrayList<>();

        // Map IDs to Names
        List<String> genreNames = ids.stream()
                .map(GENRE_MAP::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        model.setGenres(genreNames);

        boolean isAdult = Boolean.TRUE.equals(item.getAdult());
        model.setAdult(isAdult);
        model.setMaturityRating(isAdult ? "A" : "U/A");

        return model;
    }

    // Helper method to extract year from date string (format: "YYYY-MM-DD")
    private String extractYear(String dateString) {
        if (dateString == null || dateString.isEmpty())
            return null;
        try {
            return String.valueOf(LocalDate.parse(dateString).getYear());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}