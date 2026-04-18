package com.riyura.backend.common.port;

import com.riyura.backend.common.dto.health.HealthCheckResponse;

public interface HealthServicePort {
    HealthCheckResponse check();
}
