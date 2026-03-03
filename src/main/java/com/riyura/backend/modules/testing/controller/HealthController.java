package com.riyura.backend.modules.testing.controller;

import com.riyura.backend.common.dto.health.HealthCheckResponse;
import com.riyura.backend.common.model.HealthStatus;
import com.riyura.backend.common.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test/health")
@RequiredArgsConstructor
public class HealthController {

        private final HealthService healthService;

        // Returns a structured health snapshot for all critical infrastructure
        @GetMapping
        public ResponseEntity<HealthCheckResponse> getHealth() {
                HealthCheckResponse response = healthService.check();
                HttpStatus status = response.getStatus() == HealthStatus.DOWN
                                ? HttpStatus.SERVICE_UNAVAILABLE
                                : HttpStatus.OK;
                return ResponseEntity.status(status).body(response);
        }
}
