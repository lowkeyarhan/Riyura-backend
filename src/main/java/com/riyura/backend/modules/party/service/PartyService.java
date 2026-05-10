package com.riyura.backend.modules.party.service;

import com.riyura.backend.common.config.RedisConfig;
import com.riyura.backend.modules.party.dto.ChatMessage;
import com.riyura.backend.modules.party.dto.PartyCreateRequest;
import com.riyura.backend.modules.party.dto.PartyMessage;
import com.riyura.backend.modules.party.dto.PartyStateResponse;
import com.riyura.backend.modules.party.dto.SyncCommand;
import com.riyura.backend.modules.party.model.PartyEvent;
import com.riyura.backend.modules.party.model.PartyState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartyService implements com.riyura.backend.modules.party.port.PartyServicePort {

    private static final String KEY_PREFIX = "party:";
    private static final int MAX_CHAT_HISTORY = 50;
    private static final int MAX_PARTICIPANTS = 20;
    private static final long HEARTBEAT_TIMEOUT_MS = 45_000L;

    // PartyId must be alphanumeric, 1-20 characters
    private static final Pattern PARTY_ID_PATTERN = Pattern.compile("^[A-Za-z0-9]{1,20}$");

    private final RedisTemplate<String, Object> redisTemplate;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    // This is the method that is used to create a new party
    public PartyState createParty(String hostId, PartyCreateRequest request) {
        String partyId = generatePartyId();

        PartyState state = new PartyState();
        state.setPartyId(partyId);
        state.setHostId(hostId);
        state.setTmdbId(request.getTmdbId());
        state.setMediaType(request.getMediaType());
        state.setSeasonNo(request.getSeasonNo());
        state.setEpisodeNo(request.getEpisodeNo());
        state.setProviderId(request.getProviderId());
        state.setStartAt(request.getStartAt() != null ? request.getStartAt() : 0.0);
        state.setPartyStartedAt(Instant.now().toEpochMilli());
        state.setStrictSync(false);
        state.getParticipantIds().add(hostId);
        state.getParticipantNames().put(hostId, hostId);
        state.getLastHeartbeat().put(hostId, Instant.now().toEpochMilli());

        save(state);
        log.info("Party [{}] created by host [{}]", partyId, hostId);
        return state;
    }

    // This is the method that is used to get the state of a party
    public PartyStateResponse getState(String partyId) {
        validatePartyId(partyId);
        PartyState state = load(partyId);
        return PartyStateResponse.from(state);
    }

    // Returns the raw party state (e.g. for WebSocket sync broadcasts)
    public PartyState getPartyState(String partyId) {
        validatePartyId(partyId);
        return load(partyId);
    }

    // This is the method that is used to add a participant to a party
    public PartyState addParticipant(String partyId, String userId, String userName) {
        validatePartyId(partyId);
        PartyState state = load(partyId);

        if (!state.getParticipantIds().contains(userId)) {
            if (state.getParticipantIds().size() >= MAX_PARTICIPANTS) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Party is full (max " + MAX_PARTICIPANTS + " participants)");
            }
            state.getParticipantIds().add(userId);
            log.info("User [{}] joined party [{}]", userId, partyId);
        }
        participantNames(state).put(userId, displayName(userId, userName));
        state.getLastHeartbeat().put(userId, Instant.now().toEpochMilli());
        save(state);
        return state;
    }

    // This is the method that is used to remove a participant from a party
    public PartyState handleDisconnect(String partyId, String userId, String userName) {
        validatePartyId(partyId);
        PartyState state = loadOrNull(partyId);
        if (state == null)
            return null;

        String departingName = displayName(userId, userName, state);
        state.getParticipantIds().remove(userId);
        state.getBufferingParticipants().remove(userId);
        state.getLastHeartbeat().remove(userId);
        log.info("User [{}] left party [{}]. Remaining: {}", userId, partyId, state.getParticipantIds().size());

        String topic = "/topic/party/" + partyId;

        if (state.getParticipantIds().isEmpty()) {
            // Last participant — destroy the party
            delete(partyId);
            log.info("Party [{}] destroyed — no participants remaining.", partyId);
            messagingTemplate.convertAndSend(topic, new PartyMessage(
                    PartyEvent.PARTY_CLOSED,
                    Map.of("reason", "All participants left"),
                    userId,
                    Instant.now().toEpochMilli()));
            return null;
        }

        messagingTemplate.convertAndSend(topic, new PartyMessage(
                PartyEvent.USER_LEFT,
                Map.of("userId", userId, "userName", departingName, "participantIds", state.getParticipantIds()),
                userId,
                Instant.now().toEpochMilli()));

        // Host migration
        if (userId.equals(state.getHostId())) {
            String newHost = state.getParticipantIds().get(0);
            state.setHostId(newHost);
            log.info("Party [{}] host migrated from [{}] to [{}]", partyId, userId, newHost);
            messagingTemplate.convertAndSend(topic, new PartyMessage(
                    PartyEvent.NEW_HOST_ASSIGNED,
                    Map.of("newHostId", newHost, "newHostName", displayName(newHost, null, state)),
                    "system",
                    Instant.now().toEpochMilli()));
        }

        participantNames(state).remove(userId);
        save(state);
        return state;
    }

    // This is the method that is used to record a heartbeat for a participant
    public void recordHeartbeat(String partyId, String userId) {
        validatePartyId(partyId);
        PartyState state = loadOrNull(partyId);
        if (state == null)
            return;
        state.getLastHeartbeat().put(userId, Instant.now().toEpochMilli());
        save(state);
    }

    // This is the method that is used to evict any participant whose last heartbeat
    // is older than HEARTBEAT_TIMEOUT_MS
    public PartyState evictZombies(String partyId) {
        validatePartyId(partyId);
        PartyState state = loadOrNull(partyId);
        if (state == null)
            return null;

        long now = Instant.now().toEpochMilli();
        var zombies = state.getLastHeartbeat().entrySet().stream()
                .filter(e -> (now - e.getValue()) > HEARTBEAT_TIMEOUT_MS)
                .map(java.util.Map.Entry::getKey)
                .toList();

        for (String zombie : zombies) {
            log.warn("Evicting zombie participant [{}] from party [{}]", zombie, partyId);
            state = handleDisconnect(partyId, zombie, null);
            if (state == null)
                return null; // party destroyed
        }
        return state;
    }

    // Applies a playback sync command and persists the latest party position.
    public PartyState applySync(String partyId, String userId, SyncCommand command) {
        validatePartyId(partyId);
        PartyState state = load(partyId);

        if (command == null || command.getAction() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sync action is required");
        }

        // In strict sync only the host can push timeline changes. When strict sync is
        // off, participants may push their position too.
        if (!userId.equals(state.getHostId()) && state.isStrictSync()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can issue sync commands");
        }

        state.setStartAt(requireValidStartAt(command.getStartAt()));

        if (command.getProviderId() != null && !command.getProviderId().isBlank()) {
            state.setProviderId(command.getProviderId().trim());
        }

        save(state);
        return state;
    }

    // Persists a host-driven provider/server switch for the party.
    public PartyState changeProvider(String partyId, String userId, String providerId) {
        validatePartyId(partyId);
        PartyState state = load(partyId);

        if (!userId.equals(state.getHostId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can change provider");
        }

        if (providerId == null || providerId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerId is required");
        }

        state.setProviderId(providerId.trim());
        save(state);
        return state;
    }

    // This is the method that is used to append a chat message to a party
    public PartyState appendChat(String partyId, ChatMessage message) {
        validatePartyId(partyId);
        message.setText(HtmlUtils.htmlEscape(message.getText()));
        if (message.getSenderDisplayName() != null) {
            message.setSenderDisplayName(HtmlUtils.htmlEscape(message.getSenderDisplayName()));
        }
        if (message.getSenderProfilePhoto() != null) {
            // It's a URL, but we will escape it just to be safe
            message.setSenderProfilePhoto(HtmlUtils.htmlEscape(message.getSenderProfilePhoto(), "UTF-8"));
        }

        PartyState state = load(partyId);
        var chat = state.getRecentChat();
        chat.add(message);
        if (chat.size() > MAX_CHAT_HISTORY) {
            chat.remove(0);
        }
        save(state);
        return state;
    }

    // This is the method that is used to mark a participant as buffering
    public PartyState markBuffering(String partyId, String userId) {
        validatePartyId(partyId);
        PartyState state = load(partyId);
        if (state.getBufferingParticipants().contains(userId)) {
            return null;
        }
        state.getBufferingParticipants().add(userId);
        save(state);
        return state.isStrictSync() ? state : null;
    }

    // This is the method that is used to clear a participant's buffering status
    public PartyState markBufferingComplete(String partyId, String userId) {
        validatePartyId(partyId);
        PartyState state = load(partyId);
        if (!state.getBufferingParticipants().remove(userId)) {
            return null;
        }
        save(state);
        return state.isStrictSync() && state.getBufferingParticipants().isEmpty() ? state : null;
    }

    // This is the method that is used to toggle strict-sync mode
    public boolean toggleStrictSync(String partyId, String hostId) {
        validatePartyId(partyId);
        PartyState state = load(partyId);
        if (!hostId.equals(state.getHostId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can toggle strict sync");
        }
        state.setStrictSync(!state.isStrictSync());
        save(state);
        return state.isStrictSync();
    }

    // Validates partyId format to prevent Redis key injection
    private void validatePartyId(String partyId) {
        if (partyId == null || !PARTY_ID_PATTERN.matcher(partyId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid party ID");
        }
    }

    private double requireValidStartAt(Double startAt) {
        if (startAt == null || !Double.isFinite(startAt) || startAt < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startAt must not be negative");
        }
        return startAt;
    }

    private String displayName(String userId, String suppliedName) {
        if (suppliedName != null && !suppliedName.isBlank()) {
            return suppliedName.trim();
        }
        return userId;
    }

    private String displayName(String userId, String suppliedName, PartyState state) {
        if (suppliedName != null && !suppliedName.isBlank()) {
            return suppliedName.trim();
        }
        String storedName = participantNames(state).get(userId);
        if (storedName != null && !storedName.isBlank()) {
            return storedName.trim();
        }
        return userId;
    }

    private Map<String, String> participantNames(PartyState state) {
        if (state.getParticipantNames() == null) {
            state.setParticipantNames(new java.util.HashMap<>());
        }
        return state.getParticipantNames();
    }

    // Helper method to load a party state from the database
    private PartyState load(String partyId) {
        PartyState state = loadOrNull(partyId);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Party not found");
        }
        return state;
    }

    // Helper method to load a party state from the database or return null if the
    // party is not found
    private PartyState loadOrNull(String partyId) {
        Object raw = redisTemplate.opsForValue().get(KEY_PREFIX + partyId);
        if (raw instanceof PartyState ps)
            return ps;
        return null;
    }

    // Helper method to save a party state to the database
    private void save(PartyState state) {
        redisTemplate.opsForValue().set(KEY_PREFIX + state.getPartyId(), state,
                RedisConfig.PARTY_TTL_SECONDS, TimeUnit.SECONDS);
    }

    // Helper method to delete a party state from the database
    private void delete(String partyId) {
        redisTemplate.delete(KEY_PREFIX + partyId);
    }

    // Helper method to generate a party ID
    private String generatePartyId() {
        long high = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        return Long.toString(high, 36).toUpperCase();
    }
}
