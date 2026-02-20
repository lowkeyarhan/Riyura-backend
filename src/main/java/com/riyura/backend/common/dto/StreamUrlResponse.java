package com.riyura.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StreamUrlResponse {
    private String id;
    private String name;
    private String url;
    private String quality;
}
