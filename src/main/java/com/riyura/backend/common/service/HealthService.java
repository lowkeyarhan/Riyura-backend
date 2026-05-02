package com.riyura.backend.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.riyura.backend.common.dto.health.HealthCheckResponse;
import com.riyura.backend.common.dto.health.HealthComponentDetail;
import com.riyura.backend.common.model.HealthStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.riyura.backend.common.port.HealthServicePort;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthService implements HealthServicePort {

    private static final String DB_PROBE_QUERY = "SELECT 1";
    private static final String REDIS_PING_RESPONSE = "PONG";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    // Runs all component probes and returns the aggregate health snapshot.
    public HealthCheckResponse check() {
        Map<String, HealthComponentDetail> components = new LinkedHashMap<>();

        HealthComponentDetail dbDetail = probeDatabase();
        HealthComponentDetail redisDetail = probeRedis();

        components.put("database", dbDetail);
        components.put("redis", redisDetail);

        HealthStatus aggregate = aggregateStatus(dbDetail.getStatus(), redisDetail.getStatus());

        return HealthCheckResponse.builder()
                .status(aggregate)
                .components(components)
                .checkedAt(Instant.now())
                .build();
    }

    // Probes the database for liveness.
    private HealthComponentDetail probeDatabase() {
        long start = System.currentTimeMillis();
        try {
            // If the database is reachable, the query will return 1.
            jdbcTemplate.queryForObject(DB_PROBE_QUERY, Integer.class);
            long latency = System.currentTimeMillis() - start;
            log.debug("Database probe OK – {}ms", latency);
            return HealthComponentDetail.builder()
                    .status(HealthStatus.UP)
                    .latencyMs(latency)
                    .build();
        } catch (DataAccessException ex) {
            // If the database is unreachable, the query will throw a DataAccessException.
            long latency = System.currentTimeMillis() - start;
            log.error("Database probe FAILED – {}ms – {}", latency, ex.getMessage());
            return HealthComponentDetail.builder()
                    .status(HealthStatus.DOWN)
                    .latencyMs(latency)
                    .errorMessage("Database is unreachable: " + ex.getMostSpecificCause().getMessage())
                    .build();
        }
    }

    // Probes Redis for liveness.
    private HealthComponentDetail probeRedis() {
        long start = System.currentTimeMillis();
        try (org.springframework.data.redis.connection.RedisConnection connection = stringRedisTemplate
                .getConnectionFactory().getConnection()) {
            // If the Redis is reachable, the ping will return "PONG".
            String pong = connection.ping();
            long latency = System.currentTimeMillis() - start;
            if (REDIS_PING_RESPONSE.equalsIgnoreCase(pong)) {
                log.debug("Redis probe OK – {}ms", latency);
                return HealthComponentDetail.builder()
                        .status(HealthStatus.UP)
                        .latencyMs(latency)
                        .build();
            }
            // If the Redis is unreachable, the ping will return an unexpected response.
            log.warn("Redis probe returned unexpected response '{}' – {}ms", pong, latency);
            return HealthComponentDetail.builder()
                    .status(HealthStatus.DEGRADED)
                    .latencyMs(latency)
                    .errorMessage("Unexpected PING response from Redis: " + pong)
                    .build();
        } catch (RedisConnectionFailureException | IllegalStateException ex) {
            long latency = System.currentTimeMillis() - start;
            log.warn("Redis probe DEGRADED – {}ms – {}", latency, ex.getMessage());
            return HealthComponentDetail.builder()
                    .status(HealthStatus.DEGRADED)
                    .latencyMs(latency)
                    .errorMessage("Redis is unreachable (caching disabled): " + ex.getMessage())
                    .build();
        }
    }

    // Derives the aggregate application status using a worst-wins strategy.
    private HealthStatus aggregateStatus(HealthStatus db, HealthStatus redis) {
        if (db == HealthStatus.DOWN) {
            return HealthStatus.DOWN;
        }
        if (db == HealthStatus.DEGRADED || redis == HealthStatus.DEGRADED) {
            return HealthStatus.DEGRADED;
        }
        return HealthStatus.UP;
    }
}
