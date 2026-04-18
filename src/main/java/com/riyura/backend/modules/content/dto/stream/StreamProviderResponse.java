package com.riyura.backend.modules.content.dto.stream;

import com.riyura.backend.modules.content.model.StreamProvider;
import lombok.Builder;
import lombok.Data;

/**
 * API DTO for stream provider data.
 * Stripped of JPA annotations — the entity is {@link StreamProvider}.
 */
@Data
@Builder
public class StreamProviderResponse {

    private String providerId;
    private String providerName;
    private String movieTemplate;
    private String tvTemplate;
    private String animeTemplate;
    private String quality;
    private Integer priority;
    private Boolean isActive;

    /**
     * Maps the JPA entity to this DTO.
     */
    public static StreamProviderResponse from(StreamProvider entity) {
        return StreamProviderResponse.builder()
                .providerId(entity.getProviderId())
                .providerName(entity.getProviderName())
                .movieTemplate(entity.getMovieTemplate())
                .tvTemplate(entity.getTvTemplate())
                .animeTemplate(entity.getAnimeTemplate())
                .quality(entity.getQuality())
                .priority(entity.getPriority())
                .isActive(entity.getIsActive())
                .build();
    }
}