package com.riyura.backend.common.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.riyura.backend.common.dto.StreamProviderRequest;
import com.riyura.backend.common.dto.StreamProviderResponse;
import com.riyura.backend.common.dto.StreamUrlResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.repository.StreamProviderRepository;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StreamUrlService {

    private final StreamProviderRepository streamProviderRepository;
    private final RestTemplate restTemplate;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    // Builds fully-substituted stream URLs for a given media item.
    // isAnime is detected automatically via a TMDB lookup.
    public List<StreamUrlResponse> buildStreamUrls(StreamProviderRequest request, MediaType mediaType) {
        List<StreamProviderResponse> providers = streamProviderRepository.findByIsActiveTrueOrderByPriorityAsc();
        List<StreamUrlResponse> results = new ArrayList<>();

        boolean isAnime = detectIsAnime(request.getTmdbId(), mediaType);

        for (StreamProviderResponse provider : providers) {
            String template = resolveTemplate(provider, mediaType, isAnime);
            if (template == null)
                continue;

            String url = substituteParams(template, request, mediaType);

            StreamUrlResponse response = new StreamUrlResponse();
            response.setId(provider.getProviderId());
            response.setName(provider.getProviderName());
            response.setUrl(url);
            response.setQuality(provider.getQuality());
            results.add(response);
        }

        return results;
    }

    // Calls TMDB to determine whether the media item is anime
    // (Japanese original language + Animation genre). Defaults to false on error.
    private boolean detectIsAnime(long tmdbId, MediaType mediaType) {
        try {
            String path = mediaType == MediaType.Movie ? "movie" : "tv";
            String url = String.format("%s/%s/%d?api_key=%s&language=en-US", baseUrl, path, tmdbId, apiKey);
            TmdbMediaDetails details = restTemplate.getForObject(url, TmdbMediaDetails.class);
            if (details == null)
                return false;
            return isJapanese(details.getOriginalLanguage()) && hasAnimationGenre(details.getGenres());
        } catch (Exception e) {
            return false;
        }
    }

    // Checks if the original language is Japanese.
    private boolean isJapanese(String lang) {
        return "ja".equalsIgnoreCase(lang);
    }

    // Checks if the genres include Animation (either by name or TMDB genre ID 16).
    private boolean hasAnimationGenre(List<TmdbMediaDetails.Genre> genres) {
        if (genres == null || genres.isEmpty())
            return false;
        return genres.stream()
                .anyMatch(g -> "Animation".equals(g.getName()) || (g.getId() != null && g.getId() == 16));
    }

    // Picks the right template based on media type and anime flag.
    private String resolveTemplate(StreamProviderResponse provider, MediaType mediaType, boolean isAnime) {
        if (isAnime) {
            String animeTemplate = provider.getAnimeTemplate();
            return animeTemplate != null ? animeTemplate : provider.getTvTemplate();
        }
        if (mediaType == MediaType.Movie) {
            return provider.getMovieTemplate();
        }
        return provider.getTvTemplate();
    }

    // Substitutes all placeholders in the URL template.
    private String substituteParams(String template, StreamProviderRequest request, MediaType mediaType) {
        String url = template;

        // Required parameters — support both {id} and {tmdbId} placeholder variants
        String tmdbIdStr = String.valueOf(request.getTmdbId());
        url = url.replace("{id}", tmdbIdStr);
        url = url.replace("{tmdbId}", tmdbIdStr);

        // Season/episode — default to 1 if not provided (0 means not set for
        // primitives). Support both {season}/{episode} and {s}/{e} template variants.
        if (mediaType != MediaType.Movie) {
            int season = request.getSeasonNo() > 0 ? request.getSeasonNo() : 1;
            int episode = request.getEpisodeNo() > 0 ? request.getEpisodeNo() : 1;
            url = url.replace("{season}", String.valueOf(season));
            url = url.replace("{episode}", String.valueOf(episode));
            url = url.replace("{s}", String.valueOf(season));
            url = url.replace("{e}", String.valueOf(episode));
        }

        // startAt — only substitute when provided, otherwise strip the whole query
        // param
        if (request.getStartAt() != null) {
            url = url.replace("{startAt}", String.valueOf(request.getStartAt()));
        } else {
            url = stripQueryParam(url, "{startAt}");
        }

        // Strip any remaining unfilled optional placeholders (e.g. {progress},
        // {duration})
        url = stripRemainingPlaceholders(url);

        return url;
    }

    // Removes a query parameter whose value equals the given placeholder string.
    // Handles both ?key=placeholder (first param) and &key=placeholder
    // (subsequent).
    private String stripQueryParam(String url, String placeholder) {
        // Match &key=<placeholder> or ?key=<placeholder>
        String encoded = placeholder.replace("{", "\\{").replace("}", "\\}");
        // Remove as a non-first query param: &anything=<placeholder>
        url = url.replaceAll("&[^&?=]+=" + encoded, "");
        // Remove as first query param: ?anything=<placeholder> — replace ? with nothing
        // if no params remain, else fix up
        url = url.replaceAll("\\?[^&?=]+=" + encoded + "(&|$)", "?$1");
        // Clean up any leading ? with no key following it
        url = url.replaceAll("\\?&", "?");
        url = url.replaceAll("\\?$", "");
        return url;
    }

    // After known substitutions, strip any remaining {placeholder} patterns and
    // their
    // parent query param key entirely from the URL.
    private String stripRemainingPlaceholders(String url) {
        // Remove &key={anything}
        url = url.replaceAll("&[^&?=]+=\\{[^}]*\\}", "");
        // Remove ?key={anything} as first param, promote next param to first if exists
        url = url.replaceAll("\\?[^&?=]+=\\{[^}]*\\}(&|$)", "?$1");
        // Fix ?& → ?
        url = url.replaceAll("\\?&", "?");
        // Fix trailing ?
        url = url.replaceAll("\\?$", "");
        return url;
    }

    // Minimal TMDB model for anime detection — avoids coupling to content-module
    // DTOs.
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TmdbMediaDetails {

        @JsonProperty("original_language")
        private String originalLanguage;

        private List<Genre> genres;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Genre {
            private Integer id;
            private String name;
        }
    }
}
