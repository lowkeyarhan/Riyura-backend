package com.riyura.backend.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class ClientIdentifierProvider {

    // Resolve the client identifier for the request
    public String resolve(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            Object principal = auth.getPrincipal();
            if (principal instanceof Jwt jwt) {
                return "user:" + jwt.getSubject();
            }
        }

        return "ip:" + extractClientIp(request);
    }

    // Extract the client IP from the request
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String ip = forwarded.split(",")[0].trim();
            // Sanitize: only allow valid IP-like characters to prevent injection of
            // arbitrary rate limit keys
            if (ip.matches("[0-9a-fA-F.:]+") && ip.length() <= 45) {
                return ip;
            }
        }
        return request.getRemoteAddr();
    }
}
