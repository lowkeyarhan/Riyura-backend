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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        RedisCacheWriter defaultWriter = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);

        // Redis cache writer to add jitter to the TTL
        RedisCacheWriter jitterWriter = new RedisCacheWriter() {
            @Override
            public void put(String name, byte[] key, byte[] value, Duration ttl) {
                defaultWriter.put(name, key, value, addJitter(ttl));
            }

            // Get the value from the Redis cache
            @Override
            public byte[] get(String name, byte[] key) {
                return defaultWriter.get(name, key);
            }

            // Put the value into the Redis cache if it is not present
            @Override
            public byte[] putIfAbsent(String name, byte[] key, byte[] value, Duration ttl) {
                return defaultWriter.putIfAbsent(name, key, value, addJitter(ttl));
            }

            // Remove the value from the Redis cache
            @Override
            public void remove(String name, byte[] key) {
                defaultWriter.remove(name, key);
            }

            @Override
            public void clear(String name, byte[] pattern) {
            }

            // Clear the cache statistics from the Redis cache
            @Override
            public void clearStatistics(String name) {
                defaultWriter.clearStatistics(name);
            }

            // Add statistics collector to the Redis cache
            @Override
            public RedisCacheWriter withStatisticsCollector(CacheStatisticsCollector cacheStatisticsCollector) {
                return defaultWriter.withStatisticsCollector(cacheStatisticsCollector);
            }

            // Get the cache statistics from the Redis cache
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

            // Retrieve the value from the Redis cache
            public CompletableFuture<byte[]> retrieve(String name, byte[] key) {
                return CompletableFuture.completedFuture(get(name, key));
            }

            @Override
            // Retrieve the value from the Redis cache with a TTL
            public CompletableFuture<byte[]> retrieve(String name, byte[] key, Duration ttl) {
                return defaultWriter.retrieve(name, key, ttl);
            }

            @Override
            // Evict the value from the Redis cache
            public void evict(String name, byte[] key) {
                defaultWriter.remove(name, key);
            }

            // Add jitter to the TTL
            private Duration addJitter(Duration ttl) {
                if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                    return ttl;
                }
                long seconds = ttl.getSeconds();
                long jitter = (long) (seconds * ThreadLocalRandom.current().nextDouble(0.10, 0.20));
                return ttl.plusSeconds(jitter);
            }
        };

        // Configuration for the Redis cache
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofDays(1)) // Base TTL of 1 day
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // Build the Redis cache manager
        return RedisCacheManager.builder(jitterWriter)
                .cacheDefaults(config)
                .build();
    }

    // Dedicated thread pool for background cache refreshes
    @Bean(name = "cacheRefreshExecutor")
    public Executor cacheRefreshExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadFactory(Thread.ofVirtual().name("cache-refresh-", 0).factory());
        executor.setCorePoolSize(0);
        executor.setMaxPoolSize(Integer.MAX_VALUE);
        executor.setQueueCapacity(0);
        executor.initialize();
        return executor;
    }
}
