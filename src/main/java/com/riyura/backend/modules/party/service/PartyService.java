package com.riyura.backend.modules.party.service;

import com.riyura.backend.common.config.RedisConfig;
import com.riyura.backend.modules.party.dto.ChatMessage;
import com.riyura.backend.modules.party.dto.PartyCreateRequest;
import com.riyura.backend.modules.party.dto.PartyStateResponse;
import com.riyura.backend.modules.party.model.PartyState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartyService {

    private static final String KEY_PREFIX = "party:";
    private static final int MAX_CHAT_HISTORY = 50;
    /**
     * Heartbeats are expected every 15 s; 3 misses = 45 s grace period before
     * eviction.
     */
    private static final long HEARTBEAT_TIMEOUT_MS = 45_000L;

    private final RedisTemplate<String, Object> redisTemplate;

    // ─── REST operations ──────────────────────────────────────────────────────

    /**
     * Creates a new party, stores it in Redis with a TTL, and returns the party ID.
     */
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
        state.setStartAt(request.getStartAt() != null ? request.getStartAt() : 0);
        state.setPartyStartedAt(Instant.now().toEpochMilli());
        state.setStrictSync(false);
        state.getParticipantIds().add(hostId);
        state.getLastHeartbeat().put(hostId, Instant.now().toEpochMilli());

        save(state);
        log.info("Party [{}] created by host [{}]", partyId, hostId);
        return state;
    }

    /**
     * Returns the current party state for a given party ID.
     */
    public PartyStateResponse getState(String partyId) {
        PartyState state = load(partyId);
        return PartyStateResponse.from(state);
    }

    // ─── Participant management ───────────────────────────────────────────────

    /**
     * Adds a participant to the party if not already present.
     *
     * @return the updated state
     */
    public PartyState addParticipant(String partyId, String userId) {
        PartyState state = load(partyId);
        if (!state.getParticipantIds().contains(userId)) {
            state.getParticipantIds().add(userId);
            log.info("User [{}] joined party [{}]", userId, partyId);
        }
        state.getLastHeartbeat().put(userId, Instant.now().toEpochMilli());
        save(state);
        return state;
    }

    /**
     * Removes a participant. Handles host migration if the host disconnects.
     * Deletes the party from Redis entirely when the last participant leaves.
     *
     * @return the updated state, or null if the party was deleted
     */
    public PartyState handleDisconnect(String partyId, String userId) {
        PartyState state = loadOrNull(partyId);
        if (state == null)
            return null;

        state.getParticipantIds().remove(userId);
        state.getBufferingParticipants().remove(userId);
        state.getLastHeartbeat().remove(userId);
        log.info("User [{}] left party [{}]. Remaining: {}", userId, partyId, state.getParticipantIds().size());

        if (state.getParticipantIds().isEmpty()) {
            // Last participant — destroy the party
            delete(partyId);
            log.info("Party [{}] destroyed — no participants remaining.", partyId);
            return null;
        }

        // Host migration
        if (userId.equals(state.getHostId())) {
            String newHost = state.getParticipantIds().get(0);
            state.setHostId(newHost);
            log.info("Party [{}] host migrated from [{}] to [{}]", partyId, userId, newHost);
        }

        save(state);
        return state;
    }

    // ─── Heartbeat / zombie detection ────────────────────────────────────────

    /**
     * Records a heartbeat for the participant and extends the party TTL.
     */
    public void recordHeartbeat(String partyId, String userId) {
        PartyState state = loadOrNull(partyId);
        if (state == null)
            return;
        state.getLastHeartbeat().put(userId, Instant.now().toEpochMilli());
        save(state);
    }

    /**
     * Evicts any participant whose last heartbeat is older than
     * HEARTBEAT_TIMEOUT_MS.
     * Should be called periodically (e.g. from the heartbeat handler or a
     * scheduler).
     *
     * @return updated state, or null if the party was destroyed
     */
    public PartyState evictZombies(String partyId) {
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
            state = handleDisconnect(partyId, zombie);
            if (state == null)
                return null; // party destroyed
        }
        return state;
    }

    // ─── Sync ────────────────────────────────────────────────────────────────

    /**
     * Updates the party's startAt and partyStartedAt based on a host SEEK command.
     * Applies latency compensation: adjustedStartAt = startAt + latencySeconds.
     */
    public PartyState applySeek(String partyId, String hostId, int startAt, long clientTime) {
        PartyState state = load(partyId);

        if (!hostId.equals(state.getHostId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can issue sync commands");
        }

        long serverTime = Instant.now().toEpochMilli();
        long latencyMs = Math.max(0, serverTime - clientTime);
        // Compensate for round-trip: advance the position by half the one-way latency
        double compensatedStartAt = startAt + (latencyMs / 2000.0);
        state.setStartAt((int) Math.round(compensatedStartAt));
        state.setPartyStartedAt(serverTime);
        save(state);
        return state;
    }

    // ─── Chat ─────────────────────────────────────────────────────────────────

    /**
     * Appends a chat message to the rolling history (max 50) and returns the
     * updated state.
     */
    public PartyState appendChat(String partyId, ChatMessage message) {
        PartyState state = load(partyId);
        var chat = state.getRecentChat();
        chat.add(message);
        if (chat.size() > MAX_CHAT_HISTORY) {
            chat.remove(0);
        }
        save(state);
        return state;
    }

    // ─── Strict-sync / buffering ──────────────────────────────────────────────

    /**
     * Marks a participant as buffering. Returns true if FORCE_PAUSE should be
     * broadcast
     * (strictSync is enabled and this is the first bufferingParticipant).
     */
    public boolean markBuffering(String partyId, String userId) {
        PartyState state = load(partyId);
        if (!state.getBufferingParticipants().contains(userId)) {
            state.getBufferingParticipants().add(userId);
            save(state);
        }
        return state.isStrictSync();
    }

    /**
     * Clears a participant's buffering status. Returns true if RESUME should be
     * broadcast
     * (strictSync enabled and all buffering is resolved).
     */
    public boolean markBufferingComplete(String partyId, String userId) {
        PartyState state = load(partyId);
        state.getBufferingParticipants().remove(userId);
        save(state);
        return state.isStrictSync() && state.getBufferingParticipants().isEmpty();
    }

    /**
     * Toggles strict-sync mode. Only the host may call this.
     *
     * @return new strictSync value
     */
    public boolean toggleStrictSync(String partyId, String hostId) {
        PartyState state = load(partyId);
        if (!hostId.equals(state.getHostId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can toggle strict sync");
        }
        state.setStrictSync(!state.isStrictSync());
        save(state);
        return state.isStrictSync();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private PartyState load(String partyId) {
        PartyState state = loadOrNull(partyId);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Party not found: " + partyId);
        }
        return state;
    }

    private PartyState loadOrNull(String partyId) {
        Object raw = redisTemplate.opsForValue().get(KEY_PREFIX + partyId);
        if (raw instanceof PartyState ps)
            return ps;
        return null;
    }

    private void save(PartyState state) {
        redisTemplate.opsForValue().set(KEY_PREFIX + state.getPartyId(), state,
                RedisConfig.PARTY_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private void delete(String partyId) {
        redisTemplate.delete(KEY_PREFIX + partyId);
    }

    /**
     * Generates a 10-character URL-safe party ID using UUID entropy.
     * Uses base-36 encoding of the first 64 bits of a random UUID.
     */
    private String generatePartyId() {
        long high = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        return Long.toString(high, 36).toUpperCase();
    }
}
