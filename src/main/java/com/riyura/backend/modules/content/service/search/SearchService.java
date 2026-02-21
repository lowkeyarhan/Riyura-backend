package com.riyura.backend.modules.content.service.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.riyura.backend.common.dto.tmdb.TmdbTrendingResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.modules.content.dto.search.SearchResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final TmdbClient tmdbClient;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    @Value("${tmdb.image-base-url}")
    private String imageBaseUrl;

    // Performs a search across movies, TV shows, and companies, combining results
    // and sorting by rating
    public List<SearchResponse> search(String query) {
        if (query == null || query.trim().isEmpty())
            return Collections.emptyList();

        CompletableFuture<List<ScoredSearchResult>> multiTask = CompletableFuture.supplyAsync(() -> searchMulti(query));
        CompletableFuture<List<ScoredSearchResult>> companyTask = CompletableFuture
                .supplyAsync(() -> searchByCompany(query));

        Map<String, ScoredSearchResult> uniqueResults = new LinkedHashMap<>();
        companyTask.join().forEach(item -> uniqueResults.put(genKey(item.getResponse()), item));
        multiTask.join().forEach(item -> uniqueResults.putIfAbsent(genKey(item.getResponse()), item));

        return uniqueResults.values().stream()
                .sorted(Comparator.comparing(ScoredSearchResult::getRating,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(ScoredSearchResult::getResponse)
                .toList();
    }

    // Performs a multi-search on TMDb and processes results to extract movies, TV
    // shows, and top person for further discovery
    private List<ScoredSearchResult> searchMulti(String query) {
        String url = String.format("%s/search/multi?api_key=%s&language=en-US&query=%s&page=1&include_adult=false",
                baseUrl, apiKey, query);
        try {
            TmdbTrendingResponse response = tmdbClient.fetchWithRetry(url, TmdbTrendingResponse.class);
            if (response == null || response.getResults() == null)
                return Collections.emptyList();

            List<ScoredSearchResult> results = new ArrayList<>();
            Long topPersonId = null;

            for (TmdbTrendingResponse.TmdbItem item : response.getResults()) {
                if ("movie".equals(item.getMediaType()) || "tv".equals(item.getMediaType())) {
                    if (isValidItem(item))
                        results.add(mapItemToDto(item, null));
                } else if ("person".equals(item.getMediaType()) && topPersonId == null) {
                    topPersonId = item.getId();
                }
            }

            if (topPersonId != null)
                results.addAll(discoverContentByPerson(topPersonId));
            return results;
        } catch (Exception e) {
            System.err.println("Multi search error: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // Performs a search for companies matching the query and discovers content
    // associated with the top company result
    private List<ScoredSearchResult> searchByCompany(String query) {
        String url = String.format("%s/search/company?api_key=%s&query=%s&page=1", baseUrl, apiKey, query);
        try {
            TmdbCompanySearchResponse response = tmdbClient.fetchWithRetry(url, TmdbCompanySearchResponse.class);
            if (response != null && response.getResults() != null && !response.getResults().isEmpty()) {
                return discoverContentByCompany(response.getResults().get(0).getId());
            }
        } catch (Exception e) {
            System.err.println("Company search error: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    // Helper methods to discover content based on person or company ID, fetching
    // both movies and TV shows and combining results
    private List<ScoredSearchResult> discoverContentByPerson(Long personId) {
        return discoverContent("with_people", personId);
    }

    // Helper method to discover content based on company ID
    private List<ScoredSearchResult> discoverContentByCompany(Long companyId) {
        return discoverContent("with_companies", companyId);
    }

    // Core method to discover content based on a specific filter parameter (either
    // person or company), fetching and mapping results to DTOs
    private List<ScoredSearchResult> discoverContent(String filterParam, Long id) {
        CompletableFuture<List<ScoredSearchResult>> movies = CompletableFuture.supplyAsync(() -> {
            String url = String.format("%s/discover/movie?api_key=%s&language=en-US&sort_by=popularity.desc&%s=%d",
                    baseUrl, apiKey, filterParam, id);
            return fetchAndMap(url, MediaType.Movie);
        });
        CompletableFuture<List<ScoredSearchResult>> tvShows = CompletableFuture.supplyAsync(() -> {
            String url = String.format("%s/discover/tv?api_key=%s&language=en-US&sort_by=popularity.desc&%s=%d",
                    baseUrl, apiKey, filterParam, id);
            return fetchAndMap(url, MediaType.TV);
        });

        List<ScoredSearchResult> combined = new ArrayList<>(movies.join());
        combined.addAll(tvShows.join());
        return combined;
    }

    // Helper method to fetch data from TMDb and map it to ScoredSearchResult DTOs
    // based on media type
    private List<ScoredSearchResult> fetchAndMap(String url, MediaType forcedType) {
        try {
            TmdbTrendingResponse response = tmdbClient.fetchWithRetry(url, TmdbTrendingResponse.class);
            if (response == null || response.getResults() == null)
                return Collections.emptyList();
            return response.getResults().stream()
                    .filter(this::isValidItem)
                    .map(item -> mapItemToDto(item, forcedType))
                    .toList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // Validates that a TMDb item has the necessary data (like poster path) to be
    // considered a valid search result
    private boolean isValidItem(TmdbTrendingResponse.TmdbItem item) {
        return item.getPosterPath() != null && !item.getPosterPath().isEmpty();
    }

    // Maps a TMDb item to a SearchResponse DTO, handling both movies and TV shows
    // and applying the forced media type if provided
    private ScoredSearchResult mapItemToDto(TmdbTrendingResponse.TmdbItem item, MediaType forcedType) {
        SearchResponse dto = new SearchResponse();
        dto.setTmdbId(item.getId());
        dto.setDescription(item.getOverview());
        dto.setOriginalLanguage(item.getOriginalLanguage());

        if (item.getPosterPath() != null)
            dto.setPosterPath(imageBaseUrl + item.getPosterPath());

        MediaType type = forcedType;
        if (type == null && item.getMediaType() != null) {
            if ("movie".equalsIgnoreCase(item.getMediaType()))
                type = MediaType.Movie;
            else if ("tv".equalsIgnoreCase(item.getMediaType()))
                type = MediaType.TV;
        }
        dto.setMediaType(type);

        if (type == MediaType.Movie) {
            dto.setTitle(item.getTitle());
            dto.setReleaseYear(TmdbUtils.extractYear(item.getReleaseDate()));
        } else if (type == MediaType.TV) {
            dto.setTitle(item.getName());
            dto.setReleaseYear(TmdbUtils.extractYear(item.getFirstAirDate()));
        }

        return new ScoredSearchResult(dto, item.getVoteAverage());
    }

    // Generates a unique key for a SearchResponse based on its media type and TMDb
    // ID to help with deduplication
    private String genKey(SearchResponse item) {
        return item.getMediaType() + "_" + item.getTmdbId();
    }

    // Helper class to hold a SearchResponse along with its rating for sorting
    // purposes
    @Data
    private static class ScoredSearchResult {
        private final SearchResponse response;
        private final Double rating;
    }

    // Inner class to represent the company search response from TMDb, containing a
    // list of company items
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
