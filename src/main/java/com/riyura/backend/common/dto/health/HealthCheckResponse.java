package com.riyura.backend.common.dto.health;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

import com.riyura.backend.common.model.HealthStatus;

@Getter
@Builder
public class HealthCheckResponse {

    private final HealthStatus status;
    private final Map<String, HealthComponentDetail> components;
    private final Instant checkedAt;
}
