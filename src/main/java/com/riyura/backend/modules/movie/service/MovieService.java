package com.riyura.backend.modules.movie.service;

import com.riyura.backend.common.dto.MediaGridItemDTO;
import com.riyura.backend.modules.banner.dto.TmdbTrendingDTO;
import com.riyura.backend.modules.movie.model.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    @Value("${tmdb.image-base-url}")
    private String imageBaseUrl;

    // Get Now Playing Movies with a limit (e.g., top 10)
    public List<MediaGridItemDTO> getNowPlayingMovies(int limit) {
        String url = String.format("%s/movie/now_playing?api_key=%s&language=en-US&page=1", baseUrl, apiKey);
        return fetchAndMap(url, limit);
    }

    // Get Trending Movies with a limit (e.g., top 10)
    public List<MediaGridItemDTO> getTrendingMovies(int limit) {
        String url = String.format("%s/trending/movie/week?api_key=%s&language=en-US", baseUrl, apiKey);
        return fetchAndMap(url, limit);
    }

    // Get Popular Movies with a limit (e.g., top 10)
    public List<MediaGridItemDTO> getPopularMovies(int limit) {
        String url = String.format("%s/movie/popular?api_key=%s&language=en-US&page=1", baseUrl, apiKey);
        return fetchAndMap(url, limit);
    }

    // Get Upcoming Movies with a limit (e.g., top 10)
    public List<MediaGridItemDTO> getUpcomingMovies(int limit) {
        String url = String.format("%s/movie/upcoming?api_key=%s&language=en-US&page=1", baseUrl, apiKey);
        return fetchAndMap(url, limit);
    }

    // Helper method to fetch data from TMDB and map it to our DTO
    private List<MediaGridItemDTO> fetchAndMap(String url, int limit) {
        try {
            // Reusing the DTO we created for the Banner module to read TMDB response
            TmdbTrendingDTO response = restTemplate.getForObject(url, TmdbTrendingDTO.class);

            // Check if response or results are null to avoid NullPointerException
            if (response == null || response.getResults() == null) {
                return Collections.emptyList();
            }

            // Filter out items without posters and limit the results
            return response.getResults().stream()
                    .filter(item -> item.getPosterPath() != null && !item.getPosterPath().isEmpty()) // Filter items
                                                                                                     // without posters
                    .limit(limit)
                    .map(this::mapToDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error fetching movie data: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // Map TMDB item to our MediaGridItemDTO
    private MediaGridItemDTO mapToDTO(TmdbTrendingDTO.TmdbItem item) {
        MediaGridItemDTO dto = new MediaGridItemDTO();

        dto.setTmdbId(item.getId());
        dto.setTitle(item.getTitle());

        // Map Year (extract 2024 from 2024-05-12)
        dto.setYear(extractYear(item.getReleaseDate()));

        // Construct full poster URL
        if (item.getPosterPath() != null) {
            dto.setPosterUrl(imageBaseUrl + item.getPosterPath());
        }

        // Hardcode Enum to Movie
        dto.setMediaType(MediaType.Movie);

        return dto;
    }

    // Helper method to extract year from a date string (e.g., "2024-05-12" ->
    // "2024")
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