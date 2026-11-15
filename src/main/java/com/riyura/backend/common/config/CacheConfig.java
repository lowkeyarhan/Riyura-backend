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
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        RedisCacheWriter defaultWriter = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);

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
                defaultWriter.remove(name, key);
            }

            private Duration addJitter(Duration ttl) {
                if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                    return ttl;
                }
                long seconds = ttl.getSeconds();
                long jitter = (long) (seconds * ThreadLocalRandom.current().nextDouble(0.10, 0.20));
                return ttl.plusSeconds(jitter);
            }
        };

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofDays(1))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        return RedisCacheManager.builder(jitterWriter)
                .cacheDefaults(config)
                .build();
    }

    @Bean(name = "cacheRefreshExecutor")
    public Executor cacheRefreshExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int workers = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        executor.setThreadFactory(Thread.ofVirtual().name("cache-refresh-", 0).factory());
        executor.setCorePoolSize(0);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(workers * 32);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
