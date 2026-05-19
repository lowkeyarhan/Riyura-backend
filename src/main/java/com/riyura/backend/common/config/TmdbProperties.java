package com.riyura.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tmdb")
public record TmdbProperties(
        String apiKey,
        String baseUrl,
        String imageBaseUrl) {
}
