package com.riyura.backend.common.config;

import com.riyura.backend.modules.watchalong.service.party.PartyEventSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Configures Redis Pub/Sub message listener for party event streaming.
 *
 * Subscribes to pattern: party:*:events
 * All messages are routed to PartyEventSubscriber, which fans them out
 * to connected SSE clients via SseEmitterRegistry.
 */
@Configuration
public class RedisSubscriberConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            PartyEventSubscriber partyEventSubscriber) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // Subscribe to all party event channels using a pattern topic
        container.addMessageListener(
                new MessageListenerAdapter(partyEventSubscriber),
                new PatternTopic("party:*:events"));

        return container;
    }
}
