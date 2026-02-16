package com.riyura.backend.common.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.riyura.backend.common.model.MediaType;

import lombok.Data;

@Data
public class StreamUrlResponse {

    private String id;
    private String name;

    @JsonProperty("base_url")
    private String baseUrl;

    @JsonProperty("media_type")
    private MediaType mediaType;

    private String quality;

    @JsonProperty("is_active")
    private Boolean isActive;

    private Integer priority;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("updated_at")
    private OffsetDateTime updatedAt;
}
