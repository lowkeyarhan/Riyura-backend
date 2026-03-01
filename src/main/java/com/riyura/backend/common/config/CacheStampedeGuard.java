package com.riyura.backend.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
public class CacheStampedeGuard {

    private static final String LOCK_SUFFIX = ":lock";
    private static final String FRESH_SUFFIX = ":fresh";
    private static final String REFRESHING_SUFFIX = ":refreshing";
    private static final String DELTA_SUFFIX = ":delta";

    private static final long LOCK_TTL_SECONDS = 30;
    private static final double DEFAULT_DELTA_MS = 200.0;

    // Redis template for the CacheStampedeGuard
    private final RedisTemplate<String, Object> redisTemplate;
    private final Executor cacheRefreshExecutor;

    // Constructor for the CacheStampedeGuard
    public CacheStampedeGuard(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("cacheRefreshExecutor") Executor cacheRefreshExecutor) {
        this.redisTemplate = redisTemplate;
        this.cacheRefreshExecutor = cacheRefreshExecutor;
    }

    // Perform XFetch
    @SuppressWarnings("unchecked")
    public <T> T xfetch(String key, Duration ttl, double beta, Supplier<T> loader) {
        while (true) {
            Object cached;
            try {
                cached = redisTemplate.opsForValue().get(key);
            } catch (Exception e) {
                log.warn("XFetch: failed to deserialize '{}', evicting stale entry: {}", key, e.getMessage());
                redisTemplate.delete(key);
                cached = null;
            }
            Long remainingTtlMs = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);

            if (cached != null && remainingTtlMs != null && remainingTtlMs > 0) {
                // Cache is warm — apply XFetch formula
                double deltaMs = getStoredDelta(key);
                double rand = ThreadLocalRandom.current().nextDouble();
                // Recompute early when (beta * delta * -ln(rand)) > remainingTtl
                double xfetchScore = deltaMs * beta * -Math.log(rand);

                if (xfetchScore < remainingTtlMs) {
                    return (T) cached;
                }

                // XFetch triggered — try to acquire the distributed recompute lock
                Boolean lockAcquired = redisTemplate.opsForValue()
                        .setIfAbsent(key + LOCK_SUFFIX, "1", Duration.ofSeconds(LOCK_TTL_SECONDS));
                if (!Boolean.TRUE.equals(lockAcquired)) {
                    // Another node already recomputing; return the still-valid cached value
                    return (T) cached;
                }
                log.debug("XFetch: early recomputation triggered for '{}'", key);
                return recomputeAndStore(key, ttl, loader);
            }

            if (cached != null) {
                // Persistent key (TTL == -1) — serve it as-is
                return (T) cached;
            }

            // Cold miss — try to win the distributed lock
            Boolean lockAcquired = redisTemplate.opsForValue()
                    .setIfAbsent(key + LOCK_SUFFIX, "1", Duration.ofSeconds(LOCK_TTL_SECONDS));
            if (Boolean.TRUE.equals(lockAcquired)) {
                return recomputeAndStore(key, ttl, loader);
            }

            // Another thread won the lock — wait and retry until the cache is populated
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    // Perform SWR
    @SuppressWarnings("unchecked")
    public <T> T staleWhileRevalidate(String key, Duration softTtl, Duration hardTtl, Supplier<T> loader) {
        String freshKey = key + FRESH_SUFFIX;
        String refreshingKey = key + REFRESHING_SUFFIX;

        // Loop to perform SWR
        while (true) {
            Object cached;
            try {
                cached = redisTemplate.opsForValue().get(key);
            } catch (Exception e) {
                log.warn("SWR: failed to deserialize '{}', evicting stale entry: {}", key, e.getMessage());
                redisTemplate.delete(key);
                cached = null;
            }

            // Condition to check if the cache is warm and not stale
            if (cached != null) {
                Boolean isFresh = redisTemplate.hasKey(freshKey);
                if (!Boolean.TRUE.equals(isFresh)) {
                    // Trigger background refresh if cache is stale
                    Boolean lockAcquired = redisTemplate.opsForValue()
                            .setIfAbsent(refreshingKey, "1", Duration.ofSeconds(LOCK_TTL_SECONDS));
                    if (Boolean.TRUE.equals(lockAcquired)) {
                        log.debug("SWR: background refresh triggered for '{}'", key);
                        cacheRefreshExecutor.execute(
                                () -> backgroundRefresh(key, freshKey, refreshingKey, softTtl, hardTtl, loader));
                    }
                }
                return (T) cached;
            }

            // Cold miss — try to win the distributed lock
            Boolean lockAcquired = redisTemplate.opsForValue()
                    .setIfAbsent(key + LOCK_SUFFIX, "1", Duration.ofSeconds(LOCK_TTL_SECONDS));
            if (Boolean.TRUE.equals(lockAcquired)) {
                return recomputeAndStoreWithSwr(key, freshKey, softTtl, hardTtl, loader);
            }

            // Another thread won the lock — wait and retry until the cache is populated
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    // Recompute and store value with XFetch
    private <T> T recomputeAndStore(String key, Duration ttl, Supplier<T> loader) {
        try {
            long start = System.currentTimeMillis();
            T value = loader.get();
            long delta = System.currentTimeMillis() - start;
            if (value != null) {
                storeDelta(key, delta);
                redisTemplate.opsForValue().set(key, toCacheable(value), addJitter(ttl));
            }
            return value;
        } finally {
            redisTemplate.delete(key + LOCK_SUFFIX);
        }
    }

    // Recompute and store value with SWR
    private <T> T recomputeAndStoreWithSwr(
            String key, String freshKey, Duration softTtl, Duration hardTtl, Supplier<T> loader) {
        try {
            long start = System.currentTimeMillis();
            T value = loader.get();
            long delta = System.currentTimeMillis() - start;
            if (value != null) {
                storeDelta(key, delta);
                redisTemplate.opsForValue().set(key, toCacheable(value), addJitter(hardTtl));
                redisTemplate.opsForValue().set(freshKey, "1", addJitter(softTtl));
            }
            return value;
        } finally {
            // Release the cold-miss lock so waiting threads can read the populated value
            redisTemplate.delete(key + LOCK_SUFFIX);
        }
    }

    // Perform background refresh
    private <T> void backgroundRefresh(
            String key, String freshKey, String refreshingKey,
            Duration softTtl, Duration hardTtl, Supplier<T> loader) {
        try {
            long start = System.currentTimeMillis();
            T value = loader.get();
            long delta = System.currentTimeMillis() - start;
            if (value != null) {
                storeDelta(key, delta);
                redisTemplate.opsForValue().set(key, toCacheable(value), addJitter(hardTtl));
                redisTemplate.opsForValue().set(freshKey, "1", addJitter(softTtl));
                log.debug("SWR: background refresh complete for '{}'", key);
            }
        } catch (Exception e) {
            log.error("SWR: background refresh failed for '{}': {}", key, e.getMessage());
        } finally {
            redisTemplate.delete(refreshingKey);
        }
    }

    // Ensure the value is serializable by Jackson's NON_FINAL default typing.
    // Immutable/final collections (e.g. from .toList()) won't get type info,
    // so convert them to ArrayList which is non-final and gets properly wrapped.
    private Object toCacheable(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return value;
    }

    // Get stored delta from Redis
    private double getStoredDelta(String key) {
        Object deltaObj = redisTemplate.opsForValue().get(key + DELTA_SUFFIX);
        if (deltaObj instanceof Number n) {
            return n.doubleValue();
        }
        return DEFAULT_DELTA_MS;
    }

    // Store delta in Redis
    private void storeDelta(String key, long deltaMs) {
        redisTemplate.opsForValue().set(key + DELTA_SUFFIX, deltaMs, Duration.ofDays(7));
    }

    // Add jitter to TTL
    private static Duration addJitter(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative())
            return ttl;
        long seconds = ttl.getSeconds();
        long jitter = (long) (seconds * ThreadLocalRandom.current().nextDouble(0.10, 0.20));
        return ttl.plusSeconds(jitter);
    }
}
