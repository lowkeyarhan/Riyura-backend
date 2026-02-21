package com.riyura.backend.common.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.riyura.backend.common.dto.StreamProviderRequest;
import com.riyura.backend.common.dto.StreamProviderResponse;
import com.riyura.backend.common.dto.StreamUrlResponse;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.common.repository.StreamProviderRepository;
import com.riyura.backend.common.util.GenreLike;
import com.riyura.backend.common.util.TmdbUtils;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StreamUrlService {

    private final StreamProviderRepository streamProviderRepository;
    private final TmdbClient tmdbClient;

    @Value("${tmdb.api-key}")
    private String apiKey;

    @Value("${tmdb.base-url}")
    private String baseUrl;

    public List<StreamUrlResponse> buildStreamUrls(StreamProviderRequest request, MediaType mediaType) {
        List<StreamProviderResponse> providers = streamProviderRepository.findByIsActiveTrueOrderByPriorityAsc();
        List<StreamUrlResponse> results = new ArrayList<>();
        boolean isAnime = detectIsAnime(request.getTmdbId(), mediaType);

        for (StreamProviderResponse provider : providers) {
            String template = resolveTemplate(provider, mediaType, isAnime);
            if (template == null)
                continue;

            StreamUrlResponse response = new StreamUrlResponse();
            response.setId(provider.getProviderId());
            response.setName(provider.getProviderName());
            response.setUrl(substituteParams(template, request, mediaType));
            response.setQuality(provider.getQuality());
            results.add(response);
        }

        return results;
    }

    private boolean detectIsAnime(long tmdbId, MediaType mediaType) {
        try {
            String path = mediaType == MediaType.Movie ? "movie" : "tv";
            String url = String.format("%s/%s/%d?api_key=%s&language=en-US", baseUrl, path, tmdbId, apiKey);
            TmdbMediaDetails details = tmdbClient.fetch(url, TmdbMediaDetails.class);
            return details != null && TmdbUtils.isAnime(details.getOriginalLanguage(), details.getGenres());
        } catch (Exception e) {
            return false;
        }
    }

    private String resolveTemplate(StreamProviderResponse provider, MediaType mediaType, boolean isAnime) {
        if (isAnime) {
            String animeTemplate = provider.getAnimeTemplate();
            return animeTemplate != null ? animeTemplate : provider.getTvTemplate();
        }
        return mediaType == MediaType.Movie ? provider.getMovieTemplate() : provider.getTvTemplate();
    }

    private String substituteParams(String template, StreamProviderRequest request, MediaType mediaType) {
        String url = template;
        String tmdbIdStr = String.valueOf(request.getTmdbId());
        url = url.replace("{id}", tmdbIdStr).replace("{tmdbId}", tmdbIdStr);

        if (mediaType != MediaType.Movie) {
            int season = request.getSeasonNo() > 0 ? request.getSeasonNo() : 1;
            int episode = request.getEpisodeNo() > 0 ? request.getEpisodeNo() : 1;
            url = url.replace("{season}", String.valueOf(season)).replace("{episode}", String.valueOf(episode))
                    .replace("{s}", String.valueOf(season)).replace("{e}", String.valueOf(episode));
        }

        if (request.getStartAt() != null) {
            url = url.replace("{startAt}", String.valueOf(request.getStartAt()));
        } else {
            url = stripQueryParam(url, "{startAt}");
        }

        return stripRemainingPlaceholders(url);
    }

    private String stripQueryParam(String url, String placeholder) {
        String encoded = placeholder.replace("{", "\\{").replace("}", "\\}");
        url = url.replaceAll("&[^&?=]+=" + encoded, "");
        url = url.replaceAll("\\?[^&?=]+=" + encoded + "(&|$)", "?$1");
        url = url.replaceAll("\\?&", "?").replaceAll("\\?$", "");
        return url;
    }

    private String stripRemainingPlaceholders(String url) {
        url = url.replaceAll("&[^&?=]+=\\{[^}]*\\}", "");
        url = url.replaceAll("\\?[^&?=]+=\\{[^}]*\\}(&|$)", "?$1");
        url = url.replaceAll("\\?&", "?").replaceAll("\\?$", "");
        return url;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TmdbMediaDetails {

        @JsonProperty("original_language")
        private String originalLanguage;

        private List<Genre> genres;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Genre implements GenreLike {
            private Long id;
            private String name;
        }
    }
}
