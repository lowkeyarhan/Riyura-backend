package com.riyura.backend.common.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;

// This is the component that is used to manage the Redis server
@Slf4j
@Component
public class ManagedRedisServer implements SmartLifecycle {

    @Value("${redis.managed:true}")
    private boolean managed;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    private Process redisProcess;
    private volatile boolean running = false;

    // This is the method that is used to start the Redis server
    @Override
    public void start() {
        if (!managed) {
            log.info("Managed Redis disabled (redis.managed=false). Connecting to external Redis.");
            return;
        }

        try {
            String redisBin = resolveRedisBinary();
            ProcessBuilder pb = new ProcessBuilder(redisBin, "--port", String.valueOf(port));
            pb.inheritIO(); // pipe Redis stdout/stderr to Spring's console
            redisProcess = pb.start();
            running = true;
            log.info("✅ Managed Redis started on port {} (PID: {})", port, redisProcess.pid());
        } catch (IOException e) {
            log.error("❌ Failed to start managed Redis. Is redis-server installed? (brew install redis)\n{}",
                    e.getMessage());
        }
    }

    // This is the method that is used to stop the Redis server
    @Override
    @PreDestroy
    public void stop() {
        if (redisProcess != null && redisProcess.isAlive()) {
            redisProcess.destroy();
            try {
                redisProcess.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                redisProcess.destroyForcibly();
            }
            log.info("🛑 Managed Redis stopped.");
        }
        running = false;
    }

    // This is the method that is used to check if the Redis server is running
    @Override
    public boolean isRunning() {
        return running;
    }

    // This is the method that is used to get the phase of the Redis server
    @Override
    public int getPhase() {
        return Integer.MIN_VALUE;
    }

    // Helper method to resolve the redis-server binary
    private String resolveRedisBinary() {
        // Check for redis-server binary in common locations
        for (String candidate : new String[] {
                "/opt/homebrew/bin/redis-server", // Apple Silicon
                "/usr/local/bin/redis-server", // Intel Mac
        }) {
            // If the binary exists, return the path
            if (new java.io.File(candidate).exists())
                return candidate;
        }
        // If the binary does not exist, return the default path
        return "redis-server";
    }
}
