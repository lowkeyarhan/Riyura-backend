package com.riyura.backend.modules.watchalong.service.party;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Manages active SSE connections per party: partyId -> { userId -> SseEmitter }
// Thread-safe via ConcurrentHashMap; emitters auto-removed on completion, timeout, or error
@Slf4j
@Component
public class SseEmitterRegistry {

    private final ConcurrentHashMap<String, ConcurrentHashMap<UUID, SseEmitter>> registry = new ConcurrentHashMap<>();

    // Registers an SSE emitter and sets up lifecycle callbacks for automatic
    // cleanup
    public void register(String partyId, UUID userId, SseEmitter emitter) {
        registry.computeIfAbsent(partyId, id -> new ConcurrentHashMap<>()).put(userId, emitter);
        Runnable cleanup = () -> remove(partyId, userId);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        log.debug("SSE registered: partyId={}, userId={}", partyId, userId);
    }

    // Removes a specific user's emitter from the registry
    public void remove(String partyId, UUID userId) {
        ConcurrentHashMap<UUID, SseEmitter> partyEmitters = registry.get(partyId);
        if (partyEmitters != null) {
            partyEmitters.remove(userId);
            if (partyEmitters.isEmpty())
                registry.remove(partyId);
        }
        log.debug("SSE removed: partyId={}, userId={}", partyId, userId);
    }

    // Broadcasts an SSE event to all connected participants; silently removes dead
    // emitters
    public void broadcast(String partyId, SseEmitter.SseEventBuilder event) {
        ConcurrentHashMap<UUID, SseEmitter> partyEmitters = registry.get(partyId);
        if (partyEmitters == null || partyEmitters.isEmpty())
            return;

        for (Map.Entry<UUID, SseEmitter> entry : partyEmitters.entrySet()) {
            try {
                entry.getValue().send(event);
            } catch (IOException | IllegalStateException e) {
                log.warn("Failed to send SSE to userId={}, removing emitter", entry.getKey());
                remove(partyId, entry.getKey());
            }
        }
    }

    // Completes all emitters for a party and removes them from the registry (used
    // on PARTY_ENDED)
    public void completeAll(String partyId) {
        ConcurrentHashMap<UUID, SseEmitter> partyEmitters = registry.remove(partyId);
        if (partyEmitters == null)
            return;
        partyEmitters.values().forEach(emitter -> {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        });
        log.debug("All SSE emitters completed for partyId={}", partyId);
    }
}
