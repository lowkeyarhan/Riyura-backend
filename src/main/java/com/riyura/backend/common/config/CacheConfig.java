package com.riyura.backend.common.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.CacheStatisticsCollector;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        RedisCacheWriter defaultWriter = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);

        // Custom wrapper to add Jitter to TTLs on per-key basis to prevent Cache
        // Stampede
        RedisCacheWriter jitterWriter = new RedisCacheWriter() {
            @Override
            public void put(String name, byte[] key, byte[] value, Duration ttl) {
                defaultWriter.put(name, key, value, addJitter(ttl));
            }

            @Override
            public byte[] get(String name, byte[] key) {
                return defaultWriter.get(name, key);
            }

            @Override
            public byte[] putIfAbsent(String name, byte[] key, byte[] value, Duration ttl) {
                return defaultWriter.putIfAbsent(name, key, value, addJitter(ttl));
            }

            @Override
            public void remove(String name, byte[] key) {
                defaultWriter.remove(name, key);
            }

            @Override
            public void clear(String name, byte[] pattern) {
                // renamed clean to clear for Spring Data Redis 3.x support
                // In some versions it's called clean, in others it's clear.
            }

            @Override
            public void clearStatistics(String name) {
                defaultWriter.clearStatistics(name);
            }

            @Override
            public RedisCacheWriter withStatisticsCollector(CacheStatisticsCollector cacheStatisticsCollector) {
                return defaultWriter.withStatisticsCollector(cacheStatisticsCollector);
            }

            @Override
            public CacheStatistics getCacheStatistics(String cacheName) {
                return defaultWriter.getCacheStatistics(cacheName);
            }

            // Following methods might be needed in newer Spring Data Redis versions
            public CompletableFuture<Void> store(String name, byte[] key, byte[] value, Duration ttl) {
                try {
                    put(name, key, value, ttl);
                    return CompletableFuture.completedFuture(null);
                } catch (Exception e) {
                    return CompletableFuture.failedFuture(e);
                }
            }

            public CompletableFuture<byte[]> retrieve(String name, byte[] key) {
                return CompletableFuture.completedFuture(get(name, key));
            }

            @Override
            public CompletableFuture<byte[]> retrieve(String name, byte[] key, Duration ttl) {
                return defaultWriter.retrieve(name, key, ttl);
            }

            @Override
            public void evict(String name, byte[] key) {
                defaultWriter.remove(name, key); // fallback to remove
            }

            private Duration addJitter(Duration ttl) {
                if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                    return ttl;
                }
                // Add between 1 hour (3600s) and 6 hours (21600s) of jitter
                int jitterSeconds = ThreadLocalRandom.current().nextInt(3600, 21601);
                return ttl.plusSeconds(jitterSeconds);
            }
        };

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofDays(1)) // Base TTL of 1 day
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        return RedisCacheManager.builder(jitterWriter)
                .cacheDefaults(config)
                .build();
    }
}
