package com.riyura.backend.common.service;

import com.riyura.backend.common.config.TmdbProperties;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TmdbUrlBuilder {

    private final String baseUrl;
    private final String apiKey;
    private String path = "";
    private final Map<String, String> params = new LinkedHashMap<>();

    private TmdbUrlBuilder(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public static TmdbUrlBuilder from(TmdbProperties props) {
        return new TmdbUrlBuilder(props.baseUrl(), props.apiKey());
    }

    public TmdbUrlBuilder path(String path) {
        this.path = path;
        return this;
    }

    public TmdbUrlBuilder param(String key, String value) {
        if (value != null && !value.isBlank()) {
            this.params.put(key, value);
        }
        return this;
    }

    public TmdbUrlBuilder param(String key, int value) {
        this.params.put(key, String.valueOf(value));
        return this;
    }

    public TmdbUrlBuilder param(String key, long value) {
        this.params.put(key, String.valueOf(value));
        return this;
    }

    public TmdbUrlBuilder paramEncoded(String key, String value) {
        if (value != null && !value.isBlank()) {
            this.params.put(key, URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        return this;
    }

    public String build() {
        StringBuilder sb = new StringBuilder(baseUrl);
        sb.append(path);
        sb.append("?api_key=").append(apiKey);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            sb.append('&').append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }
}
