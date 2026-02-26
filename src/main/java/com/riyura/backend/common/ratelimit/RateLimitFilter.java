package com.riyura.backend.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

// Rate limit filter for the API
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // Paths excluded from rate limiting
    private static final Set<String> EXCLUDED_PREFIXES = Set.of(
            "/swagger-ui",
            "/v3/api-docs",
            "/actuator",
            "/favicon.ico");

    private final LettuceBasedProxyManager<String> proxyManager;
    private final ClientIdentifierProvider clientIdProvider;
    private final RateLimitTierService tierService;

    // A single ObjectMapper instance
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitFilter(
            LettuceBasedProxyManager<String> proxyManager,
            ClientIdentifierProvider clientIdProvider,
            RateLimitTierService tierService) {
        this.proxyManager = proxyManager;
        this.clientIdProvider = clientIdProvider;
        this.tierService = tierService;
    }

    // Check if the request is excluded from rate limiting
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return EXCLUDED_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    // Filter the request
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        RateLimitTierService.Tier tier = tierService.resolveTier(uri);
        String clientId = clientIdProvider.resolve(request);

        // Redis key format: "rate_limit:{clientId}:{tier}"
        String redisKey = "rate_limit:" + clientId + ":" + tier.name().toLowerCase();

        // Variables to store the result of the rate limit check
        boolean allowed = false;
        boolean failOpen = false;
        long remainingTokens = 0;
        long nanosToWait = 0;

        // Try to consume the bucket
        try {
            Bucket bucket = proxyManager.builder().build(redisKey, () -> tierService.configFor(tier));
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            allowed = probe.isConsumed();
            if (allowed) {
                remainingTokens = probe.getRemainingTokens();
            } else {
                nanosToWait = probe.getNanosToWaitForRefill();
            }
        } catch (Exception ex) {
            // If the Redis is unavailable, set the failOpen flag to true
            log.warn("[RATE-LIMIT] Redis unavailable — failing open. client={} uri={}", clientId, uri, ex);
            failOpen = true;
        }

        // If the request is allowed or the Redis is unavailable, set the remaining
        // tokens
        if (allowed || failOpen) {
            if (allowed) {
                response.setHeader("X-RateLimit-Remaining", String.valueOf(remainingTokens));
            }
            chain.doFilter(request, response);
        } else {
            // If the request is not allowed, write the rate limited response
            writeRateLimitedResponse(response, nanosToWait);
        }
    }

    // Write the rate limited response
    private void writeRateLimitedResponse(HttpServletResponse response, long nanosToWait) throws IOException {
        long waitSeconds = TimeUnit.NANOSECONDS.toSeconds(nanosToWait);

        // Round up partial seconds so the message is never misleadingly short
        if (TimeUnit.NANOSECONDS.toMillis(nanosToWait) % 1000 > 0) {
            waitSeconds += 1;
        }

        String waitDisplay = waitSeconds >= 60
                ? (waitSeconds / 60) + " minute(s)"
                : waitSeconds + " second(s)";

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(waitSeconds));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 429);
        body.put("error", "Too Many Requests");
        body.put("message", "You have been rate limited. Try again after " + waitDisplay + ".");

        objectMapper.writeValue(response.getWriter(), body);
    }
}
