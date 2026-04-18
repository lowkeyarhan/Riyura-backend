package com.riyura.backend.modules.content.service.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.riyura.backend.common.config.CacheStampedeGuard;
import com.riyura.backend.common.config.TmdbProperties;
import com.riyura.backend.common.dto.tmdb.TmdbTrendingResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.service.TmdbClient;
import com.riyura.backend.common.service.TmdbUrlBuilder;
import com.riyura.backend.common.util.TmdbUtils;
import com.riyura.backend.modules.content.dto.search.SearchResponse;
import com.riyura.backend.modules.content.dto.search.SearchSortOrder;
import com.riyura.backend.modules.content.port.SearchServicePort;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService implements SearchServicePort {

    private static final int PAGE_SIZE = 15;

    private final TmdbClient tmdbClient;
    private final CacheStampedeGuard cacheStampedeGuard;
    private final TmdbProperties tmdbProperties;

    @Override
    public List<SearchResponse> search(String query, int page, SearchSortOrder sortOrder) {
        if (query == null || query.trim().isEmpty())
            return Collections.emptyList();

        String normalizedQuery = query.trim().toLowerCase();
        String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);

        List<SearchResponse> allResults = cacheStampedeGuard.xfetch(
                "searchResults:" + normalizedQuery, Duration.ofDays(1), 1.0,
                () -> {
                    CompletableFuture<List<ScoredSearchResult>> multiTask = CompletableFuture
                            .supplyAsync(() -> searchMulti(encodedQuery));
                    CompletableFuture<List<ScoredSearchResult>> companyTask = CompletableFuture
                            .supplyAsync(() -> searchByCompany(encodedQuery));

                    Map<String, ScoredSearchResult> uniqueResults = new LinkedHashMap<>();
                    companyTask.orTimeout(8, TimeUnit.SECONDS).join()
                            .forEach(item -> uniqueResults.put(genKey(item.getResponse()), item));
                    multiTask.orTimeout(8, TimeUnit.SECONDS).join()
                            .forEach(item -> uniqueResults.putIfAbsent(genKey(item.getResponse()), item));

                    return uniqueResults.values().stream()
                            .sorted(Comparator.comparing(ScoredSearchResult::getRating,
                                    Comparator.nullsLast(Comparator.reverseOrder())))
                            .map(ScoredSearchResult::getResponse)
                            .toList();
                });

        if (allResults == null) {
            return Collections.emptyList();
        }

        SearchSortOrder effectiveSort = sortOrder != null ? sortOrder : SearchSortOrder.POPULARITY_DESC;
        Comparator<SearchResponse> comparator = switch (effectiveSort) {
            case POPULARITY_ASC -> Comparator.comparingDouble(
                    r -> r.getPopularity() != null ? r.getPopularity() : 0.0);
            case RELEASE_DATE_DESC -> Comparator.comparing(
                    r -> r.getReleaseYear() != null ? r.getReleaseYear() : "",
                    Comparator.reverseOrder());
            case RELEASE_DATE_ASC -> Comparator.comparing(
                    r -> r.getReleaseYear() != null ? r.getReleaseYear() : "");
            default -> Comparator.comparingDouble(
                    (SearchResponse r) -> r.getPopularity() != null ? r.getPopularity() : 0.0)
                    .reversed();
        };
        List<SearchResponse> sorted = allResults.stream().sorted(comparator).toList();

        int start = page * PAGE_SIZE;
        if (start >= sorted.size()) {
            return Collections.emptyList();
        }
        int end = Math.min(start + PAGE_SIZE, sorted.size());
        return sorted.subList(start, end);
    }

    private List<ScoredSearchResult> searchMulti(String encodedQuery) {
        String url = TmdbUrlBuilder.from(tmdbProperties)
                .path("/search/multi")
                .param("language", "en-US")
                .param("query", encodedQuery)
                .param("page", 1)
                .param("include_adult", "false")
                .build();
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
            log.error("Multi search error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<ScoredSearchResult> searchByCompany(String encodedQuery) {
        String url = TmdbUrlBuilder.from(tmdbProperties)
                .path("/search/company")
                .param("query", encodedQuery)
                .param("page", 1)
                .build();
        try {
            TmdbCompanySearchResponse response = tmdbClient.fetchWithRetry(url, TmdbCompanySearchResponse.class);
            if (response != null && response.getResults() != null && !response.getResults().isEmpty()) {
                return discoverContentByCompany(response.getResults().get(0).getId());
            }
        } catch (Exception e) {
            log.error("Company search error: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<ScoredSearchResult> discoverContentByPerson(Long personId) {
        return discoverContent("with_people", personId);
    }

    private List<ScoredSearchResult> discoverContentByCompany(Long companyId) {
        return discoverContent("with_companies", companyId);
    }

    private List<ScoredSearchResult> discoverContent(String filterParam, Long id) {
        CompletableFuture<List<ScoredSearchResult>> movies = CompletableFuture.supplyAsync(() -> {
            String url = TmdbUrlBuilder.from(tmdbProperties)
                    .path("/discover/movie")
                    .param("language", "en-US")
                    .param("sort_by", "popularity.desc")
                    .param(filterParam, id)
                    .build();
            return fetchAndMap(url, MediaType.Movie);
        });
        CompletableFuture<List<ScoredSearchResult>> tvShows = CompletableFuture.supplyAsync(() -> {
            String url = TmdbUrlBuilder.from(tmdbProperties)
                    .path("/discover/tv")
                    .param("language", "en-US")
                    .param("sort_by", "popularity.desc")
                    .param(filterParam, id)
                    .build();
            return fetchAndMap(url, MediaType.TV);
        });

        List<ScoredSearchResult> combined = new ArrayList<>(movies.orTimeout(8, TimeUnit.SECONDS).join());
        combined.addAll(tvShows.orTimeout(8, TimeUnit.SECONDS).join());
        return combined;
    }

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

    private boolean isValidItem(TmdbTrendingResponse.TmdbItem item) {
        return item.getPosterPath() != null && !item.getPosterPath().isEmpty();
    }

    private ScoredSearchResult mapItemToDto(TmdbTrendingResponse.TmdbItem item, MediaType forcedType) {
        SearchResponse dto = new SearchResponse();
        dto.setTmdbId(item.getId());
        dto.setDescription(item.getOverview());
        dto.setOriginalLanguage(item.getOriginalLanguage());
        dto.setPopularity(item.getVoteAverage());

        if (item.getPosterPath() != null)
            dto.setPosterPath(tmdbProperties.imageBaseUrl() + item.getPosterPath());

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

    private String genKey(SearchResponse item) {
        return item.getMediaType() + "_" + item.getTmdbId();
    }

    @Data
    private static class ScoredSearchResult {
        private final SearchResponse response;
        private final Double rating;
    }

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
