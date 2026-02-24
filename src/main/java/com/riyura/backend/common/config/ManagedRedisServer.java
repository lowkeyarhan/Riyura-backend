package com.riyura.backend.common.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Manages a local Redis process tied to the Spring application lifecycle.
 *
 * On startup → spawns `redis-server --port <port>` as a child process.
 * On shutdown → destroys the child process (SIGTERM).
 *
 * This is a dev-convenience component. In production you would point
 * REDIS_HOST at a real Redis instance and this bean will be skipped
 * because redis.managed=false.
 *
 * Enable via application.yaml:
 * redis:
 * managed: true # default true in dev
 */
@Slf4j
@Component
public class ManagedRedisServer implements SmartLifecycle {

    @Value("${redis.managed:true}")
    private boolean managed;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    private Process redisProcess;
    private volatile boolean running = false;

    @Override
    public void start() {
        if (!managed) {
            log.info("Managed Redis disabled (redis.managed=false). Connecting to external Redis.");
            return;
        }

        try {
            // Resolve the redis-server binary (Homebrew installs to /opt/homebrew/bin on
            // Apple Silicon)
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

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Start before everything else (lower = earlier). */
    @Override
    public int getPhase() {
        return Integer.MIN_VALUE;
    }

    private String resolveRedisBinary() {
        for (String candidate : new String[] {
                "/opt/homebrew/bin/redis-server", // Apple Silicon
                "/usr/local/bin/redis-server", // Intel Mac
        }) {
            if (new java.io.File(candidate).exists())
                return candidate;
        }
        return "redis-server"; // fallback: resolve via shell PATH
    }
}
