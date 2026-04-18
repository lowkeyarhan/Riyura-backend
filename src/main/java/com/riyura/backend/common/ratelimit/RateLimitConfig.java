package com.riyura.backend.common.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

// Rate limit configuration for the API
@Configuration
public class RateLimitConfig {

        // Create a ProxyManager for the rate limit filter
        @Bean
        public LettuceBasedProxyManager<String> rateLimitProxyManager(LettuceConnectionFactory factory) {
                Object nativeClient = factory.getNativeClient();
                RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
                ExpirationAfterWriteStrategy expiry = ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10));

                // If the native client is a RedisClusterClient, create a ProxyManager for the
                // rate limit filter
                if (nativeClient instanceof RedisClusterClient clusterClient) {
                        return LettuceBasedProxyManager.builderFor(clusterClient.connect(codec))
                                        .withExpirationStrategy(expiry)
                                        .build();
                }

                // If the native client is a RedisClient, create a ProxyManager for the rate
                // limit filter
                if (nativeClient instanceof RedisClient standaloneClient) {
                        return LettuceBasedProxyManager.builderFor(standaloneClient.connect(codec))
                                        .withExpirationStrategy(expiry)
                                        .build();
                }

                // If the native client is not a RedisClient or RedisClusterClient, throw an
                // exception
                throw new IllegalStateException(
                                "Unsupported Lettuce native client type [" + nativeClient.getClass().getName()
                                                + "]. Expected RedisClient (standalone) or RedisClusterClient (cluster).");
        }
}
