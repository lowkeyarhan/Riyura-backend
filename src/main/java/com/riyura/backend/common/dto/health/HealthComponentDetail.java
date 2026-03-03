package com.riyura.backend.common.dto.health;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.riyura.backend.common.model.HealthStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HealthComponentDetail {

    private final HealthStatus status;
    private final Long latencyMs;
    private final String errorMessage;
}
