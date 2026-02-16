package com.riyura.backend.modules.tv.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Episode {
    private int episodeNumber;
    private String name;
    private String overview;
    private String stillPath;
    private String airDate;
}
