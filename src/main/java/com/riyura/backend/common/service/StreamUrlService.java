package com.riyura.backend.common.service;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.riyura.backend.common.dto.StreamUrlResponse;
import com.riyura.backend.common.model.MediaType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StreamUrlService {

    private static final String FETCH_ACTIVE_STREAM_URLS_SQL = """
            SELECT id, name, base_url, media_type, quality, is_active, priority, created_at, updated_at
            FROM stream_urls
            WHERE LOWER(media_type) = LOWER(?)
              AND COALESCE(is_active, FALSE) = TRUE
            ORDER BY priority ASC NULLS LAST, created_at ASC
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<StreamUrlResponse> fetchStreamUrls(MediaType mediaType) {
        if (mediaType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "media_type is required");
        }

        try {
            return jdbcTemplate.query(
                    FETCH_ACTIVE_STREAM_URLS_SQL,
                    (rs, rowNum) -> {
                        StreamUrlResponse response = new StreamUrlResponse();
                        response.setId(rs.getString("id"));
                        response.setName(rs.getString("name"));
                        response.setBaseUrl(rs.getString("base_url"));
                        response.setMediaType(parseMediaType(rs.getString("media_type")));
                        response.setQuality(rs.getString("quality"));
                        response.setIsActive(rs.getObject("is_active", Boolean.class));
                        response.setPriority(rs.getObject("priority", Integer.class));
                        response.setCreatedAt(readOffsetDateTime(rs.getObject("created_at")));
                        response.setUpdatedAt(readOffsetDateTime(rs.getObject("updated_at")));
                        return response;
                    },
                    mediaType.name());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch stream URLs", e);
        }
    }

    private MediaType parseMediaType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        for (MediaType mediaType : MediaType.values()) {
            if (mediaType.name().equalsIgnoreCase(value)) {
                return mediaType;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unsupported media_type in stream_urls: " + value);
    }

    private OffsetDateTime readOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        return null;
    }
}
