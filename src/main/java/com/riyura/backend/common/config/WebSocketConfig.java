package com.riyura.backend.common.config;

import com.riyura.backend.modules.party.security.WebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    // This is the method that is used to create a virtual thread executor
    private static ThreadPoolTaskExecutor virtualThreadExecutor(String namePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadFactory(Thread.ofVirtual().name(namePrefix, 0).factory());
        executor.setCorePoolSize(0);
        executor.setMaxPoolSize(Integer.MAX_VALUE);
        executor.setQueueCapacity(0);
        executor.afterPropertiesSet();
        return executor;
    }

    // This is the method that is used to configure the message broker
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.configureBrokerChannel()
                .taskExecutor(virtualThreadExecutor("ws-broker-"));
    }

    // This is the method that is used to register the stomp endpoints
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(frontendUrl)
                .withSockJS();
    }

    // This is the method that is used to configure the client inbound channel
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(virtualThreadExecutor("ws-inbound-"));
        registration.interceptors(webSocketAuthInterceptor);
    }

    // This is the method that is used to configure the client outbound channel
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(virtualThreadExecutor("ws-outbound-"));
    }

    // This is the method that is used to configure the web socket transport
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(128 * 1024);
        registry.setSendBufferSizeLimit(512 * 1024);
        registry.setSendTimeLimit(20_000);
    }
}
