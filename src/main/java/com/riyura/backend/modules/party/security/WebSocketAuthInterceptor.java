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

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    public static final String SESSION_USER_ID = "userId";

    private final JwtDecoder jwtDecoder;

    // This is the method that is used to intercept the message and validate the JWT
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null)
            return message;

        // If the command is CONNECT, validate the JWT
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            // If the authorization header is missing or malformed, throw an exception
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("WebSocket CONNECT rejected — missing or malformed Authorization header");
                throw new org.springframework.messaging.MessagingException("Missing Authorization header");
            }

            // Get the token from the authorization header
            String token = authHeader.substring(7);
            try {
                var jwt = jwtDecoder.decode(token);

                // Get the user id from the JWT
                String userId = jwt.getSubject();

                // Store userId in session attributes for later use
                Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

                // If the session attributes are not null, put the user id in the session
                // attributes
                if (sessionAttributes != null) {
                    sessionAttributes.put(SESSION_USER_ID, userId);
                }

                // Set the Spring Security principal so @AuthenticationPrincipal works in
                // message handlers

                // Create a new authentication token
                var auth = new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

                // Set the authentication token in the accessor
                accessor.setUser(auth);

                log.debug("WebSocket CONNECT authenticated for user [{}]", userId);

            } catch (JwtException e) {
                // If the JWT is invalid, throw an exception
                log.warn("WebSocket CONNECT rejected — invalid JWT: {}", e.getMessage());
                throw new org.springframework.messaging.MessagingException("Invalid JWT: " + e.getMessage());
            }
        }
        return message;
    }
}
