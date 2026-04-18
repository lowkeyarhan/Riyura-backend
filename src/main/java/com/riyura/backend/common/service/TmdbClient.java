package com.riyura.backend.common.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class TmdbClient {

    private static final int MAX_RETRIES = 3;
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(150);

    private final RestTemplate restTemplate;

    @CircuitBreaker(name = "tmdb", fallbackMethod = "fallbackFetchWithRetry")
    public <T> T fetchWithRetry(String url, Class<T> type) {
        ResourceAccessException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return restTemplate.getForObject(url, type);
            } catch (ResourceAccessException e) {
                lastException = e;
                if (attempt == MAX_RETRIES)
                    throw e;
                sleepBeforeRetry();
            } catch (RestClientException e) {
                throw e;
            }
        }
        if (lastException != null)
            throw lastException;
        throw new IllegalStateException("fetchWithRetry: should not reach here");
    }

    @CircuitBreaker(name = "tmdb", fallbackMethod = "fallbackFetch")
    public <T> T fetch(String url, Class<T> type) {
        return restTemplate.getForObject(url, type);
    }

    public <T> T fallbackFetchWithRetry(String url, Class<T> type, Throwable t) {
        log.error("CircuitBreaker fallback triggered for TMDB fetchWithRetry. URL: {} | Error: {}", url,
                rootMessage(t));
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "TMDB service is currently unavailable. Please try again later.", t);
    }

    public <T> T fallbackFetch(String url, Class<T> type, Throwable t) {
        log.error("CircuitBreaker fallback triggered for TMDB fetch. URL: {} | Error: {}", url, rootMessage(t));
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "TMDB service is currently unavailable. Please try again later.", t);
    }

    /**
     * Virtual-thread-friendly delay. Uses Thread.sleep(Duration) which is
     * non-pinning on virtual threads (JEP 444, Java 21+).
     */
    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String rootMessage(Throwable throwable) {
        if (throwable == null)
            return "Unknown Error";
        Throwable current = throwable;
        while (current.getCause() != null)
            current = current.getCause();
        return current.getMessage();
    }
}
