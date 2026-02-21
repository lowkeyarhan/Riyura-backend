package com.riyura.backend.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class TmdbClient {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 150L;

    private final RestTemplate restTemplate;

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

    public <T> T fetch(String url, Class<T> type) {
        return restTemplate.getForObject(url, type);
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null)
            current = current.getCause();
        return current.getMessage();
    }
}
