package com.riyura.backend.modules.testing.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.redis.core.RedisCallback;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/test/cache")
@RequiredArgsConstructor
public class CacheMonitorController {

    private final RedisTemplate<String, Object> redisTemplate;

    // Suffixes created by CacheStampedeGuard for internal bookkeeping
    private static final List<String> AUX_SUFFIXES = List.of(":lock", ":fresh", ":refreshing", ":delta");

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> getMonitorPage() {
        Resource resource = new ClassPathResource("static/cache-monitor.html");
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(resource);
    }

    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        Set<String> allKeys = redisTemplate.keys("*");
        if (allKeys == null)
            allKeys = Collections.emptySet();

        List<Map<String, Object>> keys = allKeys.stream()
                .sorted()
                .map(this::describeKey)
                .toList();

        long totalKeys = keys.size();
        long auxKeys = keys.stream().filter(k -> "Auxiliary".equals(k.get("strategy"))).count();
        long cacheKeys = totalKeys - auxKeys;

        Map<String, Long> groupCounts = keys.stream()
                .filter(k -> !"Auxiliary".equals(k.get("strategy")))
                .collect(Collectors.groupingBy(k -> (String) k.get("group"), Collectors.counting()));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalKeys", totalKeys);
        summary.put("cacheKeys", cacheKeys);
        summary.put("auxKeys", auxKeys);
        summary.put("groups", groupCounts);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("summary", summary);
        response.put("keys", keys);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/all")
    public ResponseEntity<Map<String, Object>> clearAll() {
        Set<String> keys = redisTemplate.keys("*");
        long cleared = 0;
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            cleared = keys.size();
        }
        return ResponseEntity.ok(Map.of("cleared", cleared));
    }

    @DeleteMapping("/key")
    public ResponseEntity<Map<String, Object>> clearKey(@RequestParam String key) {
        Boolean deleted = redisTemplate.delete(key);
        return ResponseEntity.ok(Map.of("key", key, "deleted", Boolean.TRUE.equals(deleted)));
    }

    @DeleteMapping("/batch")
    public ResponseEntity<Map<String, Object>> clearBatch(@RequestBody List<String> keys) {
        long cleared = 0;
        if (keys != null && !keys.isEmpty()) {
            cleared = redisTemplate.delete(keys);
        }
        return ResponseEntity.ok(Map.of("cleared", cleared));
    }

    @DeleteMapping("/pattern")
    public ResponseEntity<Map<String, Object>> clearPattern(@RequestParam String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        long cleared = 0;
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            cleared = keys.size();
        }
        return ResponseEntity.ok(Map.of("pattern", pattern, "cleared", cleared));
    }

    @GetMapping(value = "/value", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getCacheValue(@RequestParam String key) {
        try {
            byte[] rawBytes = redisTemplate.execute((RedisCallback<byte[]>) connection ->
                    connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8)));

            if (rawBytes == null) {
                return ResponseEntity.ok(Map.of("key", key, "found", false));
            }

            String rawValue = new String(rawBytes, StandardCharsets.UTF_8);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("key", key);
            result.put("found", true);
            result.put("value", rawValue);
            result.put("sizeBytes", rawBytes.length);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("key", key, "found", false, "error", e.getMessage()));
        }
    }

    private Map<String, Object> describeKey(String key) {
        Long ttlMs = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);

        Long ttlSeconds = null;
        if (ttlMs != null) {
            ttlSeconds = ttlMs > 0 ? ttlMs / 1000L : ttlMs;
        }

        String strategy = classifyStrategy(key);
        String group = classifyGroup(key);
        String status = resolveStatus(ttlSeconds, strategy);

        String displayKey = key;
        for (String suffix : AUX_SUFFIXES) {
            if (key.endsWith(suffix)) {
                displayKey = key.substring(0, key.length() - suffix.length());
                break;
            }
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("key", key);
        info.put("displayKey", displayKey);
        info.put("group", group);
        info.put("strategy", strategy);
        info.put("ttlSeconds", ttlSeconds);
        info.put("status", status);
        return info;
    }

    private String classifyGroup(String key) {
        String k = key;
        for (String suffix : AUX_SUFFIXES) {
            if (k.endsWith(suffix)) {
                k = k.substring(0, k.length() - suffix.length());
                break;
            }
        }
        if (k.startsWith("movie"))
            return "Movies";
        if (k.startsWith("tv"))
            return "TV";
        if (k.startsWith("anime"))
            return "Anime";
        if (k.startsWith("banner"))
            return "Banner";
        if (k.startsWith("explore"))
            return "Explore";
        if (k.startsWith("search"))
            return "Search";
        if (k.startsWith("watchlist"))
            return "User Data";
        if (k.startsWith("history"))
            return "User Data";
        if (k.startsWith("party"))
            return "Party";
        if (k.startsWith("rate_limit"))
            return "Rate Limits";
        return "Internal";
    }

    private String classifyStrategy(String key) {
        for (String suffix : AUX_SUFFIXES) {
            if (key.endsWith(suffix))
                return "Auxiliary";
        }
        if (key.startsWith("banners") || key.startsWith("explore"))
            return "SWR";
        if (key.contains("::"))
            return "Spring Cache";
        if (key.startsWith("party"))
            return "Redis Direct";
        if (key.startsWith("rate_limit"))
            return "Bucket4J";
        return "XFetch";
    }

    private String resolveStatus(Long ttlSeconds, String strategy) {
        if ("Auxiliary".equals(strategy))
            return "auxiliary";
        if (ttlSeconds == null || ttlSeconds == -2L)
            return "expired";
        if (ttlSeconds == -1L)
            return "persistent";
        if (ttlSeconds > 3600L)
            return "hot";
        if (ttlSeconds > 300L)
            return "warm";
        return "stale";
    }
}
