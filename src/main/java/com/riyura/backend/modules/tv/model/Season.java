package com.riyura.backend.modules.tv.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Season {
    private int seasonNumber;
    private String name;
    private String overview;
    private String posterPath;
    private String airDate;
    private List<Episode> episodes;
}
