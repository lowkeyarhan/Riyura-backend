package com.riyura.backend.modules.watchalong.service.party;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SseEmitterRegistryTest {

    private final SseEmitterRegistry registry = new SseEmitterRegistry();
    private final String partyId = "PARTY123";
    private final UUID userId = UUID.randomUUID();

    // Regression: a stale connection completing must not remove the newer connection
    // for the same user (the old code keyed connections by user id).
    @Test
    void staleConnectionCleanupDoesNotRemoveNewerConnection() throws IOException {
        SseEmitter stale = mock(SseEmitter.class);
        SseEmitter fresh = mock(SseEmitter.class);

        ArgumentCaptor<Runnable> cleanup = ArgumentCaptor.forClass(Runnable.class);
        registry.register(partyId, userId, stale);
        verify(stale).onCompletion(cleanup.capture());

        registry.register(partyId, userId, fresh);
        cleanup.getValue().run(); // stale stream completes late

        registry.broadcast(partyId, "event-1", "NEW_CHAT", "{}");

        verify(fresh).send(any(SseEmitter.SseEventBuilder.class));
        verify(stale, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    // Regression: reusing one SseEventBuilder across emitters accumulates state in
    // Spring's builder, so every connection must get its own builder.
    @Test
    void broadcastBuildsOneEventPerConnection() throws IOException {
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        registry.register(partyId, UUID.randomUUID(), first);
        registry.register(partyId, UUID.randomUUID(), second);

        registry.broadcast(partyId, "event-1", "NEW_CHAT", "{}");

        ArgumentCaptor<SseEmitter.SseEventBuilder> firstEvent = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        ArgumentCaptor<SseEmitter.SseEventBuilder> secondEvent = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(first).send(firstEvent.capture());
        verify(second).send(secondEvent.capture());
        assertNotSame(firstEvent.getValue(), secondEvent.getValue());
    }

    @Test
    void failedConnectionIsRemovedAndOthersKeepReceiving() throws IOException {
        SseEmitter dead = mock(SseEmitter.class);
        SseEmitter alive = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe")).when(dead).send(any(SseEmitter.SseEventBuilder.class));

        registry.register(partyId, UUID.randomUUID(), dead);
        registry.register(partyId, UUID.randomUUID(), alive);

        registry.broadcast(partyId, "event-1", "NEW_CHAT", "{}");
        registry.broadcast(partyId, "event-2", "NEW_CHAT", "{}");

        verify(dead, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(alive, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }
}
