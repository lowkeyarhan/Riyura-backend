package com.riyura.backend.modules.watchalong.service.party;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.watchalong.dto.party.requests.SendChatRequest;
import com.riyura.backend.modules.watchalong.interfaces.StreamUrlServicePort;
import com.riyura.backend.modules.watchalong.model.enums.PartyEventType;
import com.riyura.backend.modules.watchalong.model.enums.PartyStatus;
import com.riyura.backend.modules.watchalong.model.party.Messages;
import com.riyura.backend.modules.watchalong.model.party.Party;
import com.riyura.backend.modules.watchalong.model.party.PartyParticipants;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyServiceChatTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOps;
    @Mock
    private ListOperations<String, Object> listOps;
    @Mock
    private PartyEventPublisher eventPublisher;
    @Mock
    private StreamUrlServicePort streamUrlService;
    @Mock
    private Validator validator;

    private PartyService service;
    private final String partyId = "PARTY123";
    private final UUID senderId = UUID.randomUUID();
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        service = new PartyService(redisTemplate, eventPublisher, streamUrlService, validator);
    }

    private void stubActiveParty() {
        PartyParticipants sender = new PartyParticipants();
        sender.setUserId(senderId);
        sender.setUsername("sender");
        sender.setHost(true);
        sender.setJoinedAt(Instant.now());
        sender.setLastHeartbeat(Instant.now());

        Party party = new Party();
        party.setPartyId(partyId);
        party.setHostId(senderId);
        party.setMediaType(MediaType.Movie);
        party.setProviderId("provider");
        party.setTmdbId(1L);
        party.setStatus(PartyStatus.ACTIVE);
        party.setCreatedAt(Instant.now());
        party.setParticipants(new ArrayList<>(List.of(sender)));

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(PartyService.partyKey(partyId))).thenReturn(party);
        when(validator.validate(any())).thenReturn(Collections.emptySet());
    }

    private SendChatRequest request(UUID clientMessageId) {
        SendChatRequest request = new SendChatRequest();
        request.setPartyId(partyId.toLowerCase()); // must be normalized by the service
        request.setContent("hello");
        request.setClientMessageId(clientMessageId);
        return request;
    }

    @Test
    void storesAndPublishesOnce() throws Exception {
        stubActiveParty();
        UUID clientId = UUID.randomUUID();
        when(redisTemplate.<Long>execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(1L);

        Messages saved = service.sendChat(request(clientId), senderId, "sender", null);

        assertEquals(clientId, saved.getId());
        ArgumentCaptor<Object> stored = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), stored.capture(), any(), any());
        assertEquals(List.of(PartyService.messageKey(partyId, senderId, clientId),
                PartyService.messagesKey(partyId)), keys.getValue());
        assertEquals(clientId, json.readValue(stored.getValue().toString(), Messages.class).getId());
        verify(eventPublisher).publishEvent(eq(partyId), eq(PartyEventType.NEW_CHAT), eq(senderId),
                any(Messages.class));
    }

    @Test
    void retryWithSameClientMessageIdDoesNotStoreOrReturnANewMessage() throws Exception {
        stubActiveParty();
        UUID clientId = UUID.randomUUID();
        Messages original = new Messages();
        original.setId(clientId);
        original.setSenderId(senderId);
        original.setSenderName("sender");
        original.setContent("hello");
        original.setSentAt(Instant.parse("2026-01-01T00:00:00Z"));

        // Lua returns 0 when the dedupe key already exists
        when(redisTemplate.<Long>execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(0L);
        when(valueOps.get(PartyService.messageKey(partyId, senderId, clientId)))
                .thenReturn(json.writeValueAsString(original));

        Messages returned = service.sendChat(request(clientId), senderId, "sender", null);

        assertEquals(original.getId(), returned.getId());
        assertEquals(original.getSentAt(), returned.getSentAt());
        // Re-publish so a broadcast lost after storage is repaired; clients dedupe by message id.
        verify(eventPublisher).publishEvent(eq(partyId), eq(PartyEventType.NEW_CHAT), eq(senderId),
                any(Messages.class));
    }

    @Test
    void readingHistoryRefreshesItsTtl() throws Exception {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        Messages message = new Messages();
        message.setId(UUID.randomUUID());
        message.setSenderId(senderId);
        message.setSenderName("sender");
        message.setContent("hello");
        message.setSentAt(Instant.now());
        when(listOps.range(PartyService.messagesKey(partyId), -50, -1))
                .thenReturn(List.of(json.writeValueAsString(message)));

        List<Messages> history = service.getRecentMessages(partyId, 50);

        assertEquals(1, history.size());
        assertNotNull(history.get(0).getId());
        verify(redisTemplate).expire(eq(PartyService.messagesKey(partyId)), anyLong(), eq(TimeUnit.SECONDS));
    }
}
