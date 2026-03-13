package com.riyura.backend.modules.identity.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private UUID userId;
    private Long tmdbId;
}
