package com.riyura.backend.modules.content.service.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.riyura.backend.common.dto.TmdbTrendingResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.content.dto.search.SearchResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int TMDB_MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 150L;

    private final RestTemplate restTemplate;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    @Value("${tmdb.image-base-url}")
    private String imageBaseUrl;

    // Main Search Method
    public List<SearchResponse> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Parallel Tasks for Multi-search and Company search
        CompletableFuture<List<ScoredSearchResult>> multiTask = CompletableFuture.supplyAsync(() -> searchMulti(query));
        CompletableFuture<List<ScoredSearchResult>> companyTask = CompletableFuture
                .supplyAsync(() -> searchByCompany(query));

        // Join results from both tasks
        List<ScoredSearchResult> multiResults = multiTask.join();
        List<ScoredSearchResult> companyResults = companyTask.join();

        // Use a LinkedHashMap to maintain insertion order and ensure uniqueness based
        // on mediaType + tmdbId
        Map<String, ScoredSearchResult> uniqueResults = new LinkedHashMap<>();

        // Add Company-derived results first (optional priority)
        companyResults.forEach(item -> uniqueResults.put(genKey(item.getResponse()), item));

        // Add Multi-search results (Movies, TV, and Person-derived content)
        multiResults.forEach(item -> uniqueResults.putIfAbsent(genKey(item.getResponse()), item));

        // Sort results by rating (descending) and then map to SearchResponse DTOs
        return uniqueResults.values().stream()
                .sorted(Comparator.comparing(
                        ScoredSearchResult::getRating,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(ScoredSearchResult::getResponse)
                .collect(Collectors.toList());
    }

    // --- Search Implementations ---
    private List<ScoredSearchResult> searchMulti(String query) {
        String url = String.format("%s/search/multi?api_key=%s&language=en-US&query=%s&page=1&include_adult=false",
                baseUrl, apiKey, query);

        try {
            // Fetch multi-search results
            TmdbTrendingResponse response = fetchWithRetry(url, TmdbTrendingResponse.class);
            if (response == null || response.getResults() == null)
                return Collections.emptyList();

            // Process results to separate Movies/TV and capture top Person for discovery
            List<ScoredSearchResult> results = new ArrayList<>();
            Long topPersonId = null;

            // First pass: Add Movies/TV directly, and identify the top Person (if any)
            for (TmdbTrendingResponse.TmdbItem item : response.getResults()) {
                if ("movie".equals(item.getMediaType()) || "tv".equals(item.getMediaType())) {
                    // Direct Movie/TV hit
                    if (isValidItem(item)) {
                        results.add(mapItemToDto(item, null));
                    }

                    // Don't break here, we want to check all items for a potential Person match
                } else if ("person".equals(item.getMediaType())) {
                    // Capture the most relevant person found
                    if (topPersonId == null) {
                        topPersonId = item.getId();
                    }
                }
            }

            // If a person was found in the top results, fetch their filmography
            if (topPersonId != null) {
                results.addAll(discoverContentByPerson(topPersonId));
            }

            return results;
        } catch (Exception e) {
            System.err.println("Multi search error: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // Company search is a bit different since it doesn't return content directly,
    // we need to find the company first and then discover content associated with
    // it
    private List<ScoredSearchResult> searchByCompany(String query) {
        String url = String.format("%s/search/company?api_key=%s&query=%s&page=1", baseUrl, apiKey, query);
        try {
            TmdbCompanySearchResponse response = fetchWithRetry(url, TmdbCompanySearchResponse.class);

            // If we find a company matching the query (take the top 1)
            if (response != null && response.getResults() != null && !response.getResults().isEmpty()) {
                Long companyId = response.getResults().get(0).getId();
                return discoverContentByCompany(companyId);
            }
        } catch (Exception e) {
            System.err.println("Company search error: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    // Discover content by person (filmography)
    private List<ScoredSearchResult> discoverContentByPerson(Long personId) {
        return discoverContent("with_people", personId);
    }

    // Discover content by company
    private List<ScoredSearchResult> discoverContentByCompany(Long companyId) {
        return discoverContent("with_companies", companyId);
    }

    // Generalized discover method for both person and company filters
    private List<ScoredSearchResult> discoverContent(String filterParam, Long id) {
        // Run Movie and TV discovery in parallel
        CompletableFuture<List<ScoredSearchResult>> movies = CompletableFuture.supplyAsync(() -> {
            String url = String.format("%s/discover/movie?api_key=%s&language=en-US&sort_by=popularity.desc&%s=%d",
                    baseUrl, apiKey, filterParam, id);
            return fetchAndMap(url, MediaType.Movie);
        });

        // Run TV discovery in parallel
        CompletableFuture<List<ScoredSearchResult>> tvShows = CompletableFuture.supplyAsync(() -> {
            String url = String.format("%s/discover/tv?api_key=%s&language=en-US&sort_by=popularity.desc&%s=%d",
                    baseUrl, apiKey, filterParam, id);
            return fetchAndMap(url, MediaType.TV);
        });

        // Combine results from both discoveries
        List<ScoredSearchResult> combined = new ArrayList<>();
        combined.addAll(movies.join());
        combined.addAll(tvShows.join());
        return combined;
    }

    // Helper method to fetch data from TMDB and map it to our SearchResponse DTO
    private List<ScoredSearchResult> fetchAndMap(String url, MediaType forcedType) {
        try {
            TmdbTrendingResponse response = fetchWithRetry(url, TmdbTrendingResponse.class);
            if (response == null || response.getResults() == null)
                return Collections.emptyList();

            return response.getResults().stream()
                    .filter(this::isValidItem)
                    .map(item -> mapItemToDto(item, forcedType))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // Basic validation to ensure we only return items with posters (can be adjusted
    // based on needs)
    private boolean isValidItem(TmdbTrendingResponse.TmdbItem item) {
        // Only show items with valid posters to keep UI clean
        return item.getPosterPath() != null && !item.getPosterPath().isEmpty();
    }

    // Mapping TMDB item to our SearchResponse DTO, with handling for both Movie and
    // TV types
    private ScoredSearchResult mapItemToDto(TmdbTrendingResponse.TmdbItem item, MediaType forcedType) {
        SearchResponse dto = new SearchResponse();
        dto.setTmdbId(item.getId());
        dto.setDescription(item.getOverview());
        dto.setOriginalLanguage(item.getOriginalLanguage());

        if (item.getPosterPath() != null) {
            dto.setPosterPath(imageBaseUrl + item.getPosterPath());
        }

        // Determine Type (Use forcedType if coming from discover, else infer from item)
        MediaType type = forcedType;
        if (type == null && item.getMediaType() != null) {
            if ("movie".equalsIgnoreCase(item.getMediaType()))
                type = MediaType.Movie;
            else if ("tv".equalsIgnoreCase(item.getMediaType()))
                type = MediaType.TV;
        }
        dto.setMediaType(type);

        // Map Specific Fields
        if (type == MediaType.Movie) {
            dto.setTitle(item.getTitle());
            dto.setReleaseYear(extractYear(item.getReleaseDate()));
        } else if (type == MediaType.TV) {
            dto.setTitle(item.getName());
            dto.setReleaseYear(extractYear(item.getFirstAirDate()));
        }

        return new ScoredSearchResult(dto, item.getVoteAverage());
    }

    // Helper method to extract year from date string (handles both movie and TV
    // date formats)
    private String extractYear(String dateString) {
        if (dateString == null || dateString.isEmpty())
            return null;
        try {
            return String.valueOf(LocalDate.parse(dateString).getYear());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // Generate a unique key for each search result based on media type and TMDB ID
    // to ensure uniqueness in the final results list
    private String genKey(SearchResponse item) {
        return item.getMediaType() + "_" + item.getTmdbId();
    }

    @Data
    private static class ScoredSearchResult {
        private final SearchResponse response;
        private final Double rating;
    }

    // Helper method to fetch data with retry logic for ResourceAccessException
    private <T> T fetchWithRetry(String url, Class<T> type) {
        ResourceAccessException lastResourceException = null;

        for (int attempt = 1; attempt <= TMDB_MAX_RETRIES; attempt++) {
            try {
                return restTemplate.getForObject(url, type);
            } catch (ResourceAccessException e) {
                lastResourceException = e;
                if (attempt == TMDB_MAX_RETRIES) {
                    throw e;
                }
                sleepBeforeRetry();
            } catch (RestClientException e) {
                throw e;
            }
        }

        throw lastResourceException;
    }

    // Helper method to sleep before retrying
    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // --- Internal DTO for Company Search ---
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TmdbCompanySearchResponse {
        private List<CompanyItem> results;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class CompanyItem {
            private Long id;
            private String name;
        }
    }
}
