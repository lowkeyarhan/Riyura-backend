package com.riyura.backend.modules.watchalong.service.party;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// Connections are keyed per stream, so a stale emitter's cleanup cannot evict a newer one.
@Slf4j
@Component
public class SseEmitterRegistry {

    private record Connection(UUID userId, SseEmitter emitter) {}

    private final ConcurrentHashMap<
        String,
        ConcurrentHashMap<UUID, Connection>
    > registry = new ConcurrentHashMap<>();

    public UUID register(String partyId, UUID userId, SseEmitter emitter) {
        UUID connectionId = UUID.randomUUID();
        ConcurrentHashMap<UUID, Connection> connections =
            registry.computeIfAbsent(partyId, id -> new ConcurrentHashMap<>());
        connections.put(connectionId, new Connection(userId, emitter));

        Runnable cleanup = () -> remove(partyId, connectionId);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        log.debug(
            "SSE registered: partyId={}, userId={}, connectionId={}",
            partyId,
            userId,
            connectionId
        );
        return connectionId;
    }

    public void remove(String partyId, UUID connectionId) {
        ConcurrentHashMap<UUID, Connection> connections = registry.get(partyId);
        if (connections == null) return;
        Connection removed = connections.remove(connectionId);
        if (removed != null) log.debug(
            "SSE removed: partyId={}, userId={}, connectionId={}",
            partyId,
            removed.userId(),
            connectionId
        );
        // Atomic with computeIfAbsent on the same key: never drop a map that just gained
        // a connection between our isEmpty() check and the removal.
        registry.computeIfPresent(partyId, (id, current) ->
            current.isEmpty() ? null : current
        );
    }

    // A fresh builder per send: SseEventBuilder accumulates state when reused.
    public void broadcast(
        String partyId,
        String eventId,
        String eventName,
        String data
    ) {
        ConcurrentHashMap<UUID, Connection> connections = registry.get(partyId);
        if (connections == null || connections.isEmpty()) return;

        for (Map.Entry<UUID, Connection> entry : connections.entrySet()) {
            try {
                entry
                    .getValue()
                    .emitter()
                    .send(
                        SseEmitter.event()
                            .id(eventId)
                            .name(eventName)
                            .data(data)
                    );
            } catch (IOException | IllegalStateException e) {
                log.warn(
                    "Failed to send SSE to userId={}, removing connection",
                    entry.getValue().userId()
                );
                remove(partyId, entry.getKey());
            }
        }
    }

    public void completeAll(String partyId) {
        ConcurrentHashMap<UUID, Connection> connections = registry.remove(
            partyId
        );
        if (connections == null) return;
        connections.values().forEach(connection -> {
            try {
                connection.emitter().complete();
            } catch (Exception ignored) {}
        });
        log.debug("All SSE emitters completed for partyId={}", partyId);
    }

    // Proxies drop idle streams around 60s; ping to keep them open and evict dead ones.
    @Scheduled(fixedDelay = 15_000)
    public void keepAlive() {
        registry.forEach((partyId, connections) -> {
            if (connections.isEmpty()) return;
            for (Map.Entry<UUID, Connection> entry : connections.entrySet()) {
                try {
                    entry
                        .getValue()
                        .emitter()
                        .send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    remove(partyId, entry.getKey());
                }
            }
        });
    }
}
