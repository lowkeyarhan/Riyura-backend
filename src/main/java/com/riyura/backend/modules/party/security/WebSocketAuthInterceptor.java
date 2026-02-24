package com.riyura.backend.modules.party.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * STOMP channel interceptor that validates the Supabase JWT on CONNECT frames.
 * The authenticated userId is stored in the WebSocket session attributes so
 * that
 * {@link com.riyura.backend.modules.party.event.WebSocketEventListener} can
 * identify who disconnected.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    public static final String SESSION_USER_ID = "userId";

    private final JwtDecoder jwtDecoder;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null)
            return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("WebSocket CONNECT rejected — missing or malformed Authorization header");
                throw new org.springframework.messaging.MessagingException("Missing Authorization header");
            }

            String token = authHeader.substring(7);
            try {
                var jwt = jwtDecoder.decode(token);
                String userId = jwt.getSubject();

                // Store userId in session attributes for later use
                Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                if (sessionAttributes != null) {
                    sessionAttributes.put(SESSION_USER_ID, userId);
                }

                // Set the Spring Security principal so @AuthenticationPrincipal works in
                // message handlers
                var auth = new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                accessor.setUser(auth);

                log.debug("WebSocket CONNECT authenticated for user [{}]", userId);
            } catch (JwtException e) {
                log.warn("WebSocket CONNECT rejected — invalid JWT: {}", e.getMessage());
                throw new org.springframework.messaging.MessagingException("Invalid JWT: " + e.getMessage());
            }
        }
        return message;
    }
}
