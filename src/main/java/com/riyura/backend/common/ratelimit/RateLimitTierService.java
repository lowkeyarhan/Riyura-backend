package com.riyura.backend.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import org.springframework.stereotype.Service;

import java.time.Duration;

// Rate limit tier service for the API
@Service
public class RateLimitTierService {

    // Tiers for the rate limit
    public enum Tier {
        // General API calls — relaxed limit
        DEFAULT,
        // Expensive, fan-out endpoints (Explore, Search, Anime)
        HEAVY,
        // Party/WebSocket handshake endpoints — tightest limit
        PARTY
    }

    // Resolve the tier for the request
    public Tier resolveTier(String uri) {
        if (uri.startsWith("/api/explore") || uri.startsWith("/api/search") || uri.startsWith("/api/anime")) {
            return Tier.HEAVY;
        }
        if (uri.startsWith("/api/party") || uri.startsWith("/ws")) {
            return Tier.PARTY;
        }
        return Tier.DEFAULT;
    }

    // Return the BucketConfiguration for a given tier
    public BucketConfiguration configFor(Tier tier) {
        return switch (tier) {
            case HEAVY -> BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(30)
                            .refillGreedy(30, Duration.ofMinutes(1))
                            .build())
                    .build();

            case PARTY -> BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(10)
                            .refillGreedy(10, Duration.ofMinutes(1))
                            .build())
                    .build();

            default -> BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(100)
                            .refillGreedy(100, Duration.ofMinutes(1))
                            .build())
                    .build();
        };
    }
}
