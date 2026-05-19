package com.riyura.backend.modules.watchalong.service.party;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.riyura.backend.common.config.RedisConfig;
import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.watchalong.dto.party.requests.CreatePartyRequest;
import com.riyura.backend.modules.watchalong.dto.party.requests.JoinPartyRequest;
import com.riyura.backend.modules.watchalong.dto.party.requests.PartyProgressRequest;
import com.riyura.backend.modules.watchalong.dto.party.requests.SendChatRequest;
import com.riyura.backend.modules.watchalong.dto.party.responses.PartyStateResponse;
import com.riyura.backend.modules.watchalong.dto.party.responses.SyncResponse;
import com.riyura.backend.modules.watchalong.dto.stream.StreamProviderRequest;
import com.riyura.backend.modules.watchalong.interfaces.PartyServicePort;
import com.riyura.backend.modules.watchalong.interfaces.StreamUrlServicePort;
import com.riyura.backend.modules.watchalong.model.enums.PartyEventType;
import com.riyura.backend.modules.watchalong.model.enums.PartyStatus;
import com.riyura.backend.modules.watchalong.model.party.Messages;
import com.riyura.backend.modules.watchalong.model.party.Party;
import com.riyura.backend.modules.watchalong.model.party.PartyParticipants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartyService implements PartyServicePort {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PartyEventPublisher eventPublisher;
    private final StreamUrlServicePort streamUrlService;
    private final Validator validator;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // Per-party ReentrantLocks to serialize participant list mutations within one
    // JVM instance
    private final ConcurrentHashMap<String, ReentrantLock> partyLocks = new ConcurrentHashMap<>();

    private static final int MAX_PARTICIPANTS = 20;
    private static final int MAX_CHAT_MESSAGES = 200;
    private static final int PARTY_CODE_MAX_RETRIES = 10;
    // Excludes visually ambiguous chars (0/O, 1/I/L)
    private static final String CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    // Returns the Redis key for the party JSON blob
    public static String partyKey(String partyId) {
        return "party:" + partyId;
    }

    // Returns the Redis key tracking which party a user is currently in
    public static String userActivePartyKey(UUID userId) {
        return "party:user:" + userId + ":activeParty";
    }

    // Returns the Redis key for the party's chat message list
    public static String messagesKey(String partyId) {
        return "party:" + partyId + ":messages";
    }

    // Creates a new party with the caller as host; progress=0, streamUrl built
    // immediately for response
    @Override
    public PartyStateResponse createParty(CreatePartyRequest request, UUID userId, String username, String avatarUrl) {
        validateTvFields(request.getMediaType(), request.getSeasonNo(), request.getEpisodeNo());

        String existingPartyId = (String) redisTemplate.opsForValue().get(userActivePartyKey(userId));
        if (existingPartyId != null) {
            try {
                leaveParty(existingPartyId, userId);
            } catch (Exception e) {
                redisTemplate.delete(userActivePartyKey(userId));
                log.warn("Failed to cleanly leave old party {} for user {}, proceeding to create new", existingPartyId,
                        userId);
            }
        }

        String partyId = generateUniquePartyCode();
        PartyParticipants host = buildParticipant(userId, username, avatarUrl, true);

        Party party = new Party();
        party.setPartyId(partyId);
        party.setHostId(userId);
        party.setMediaType(request.getMediaType());
        party.setTmdbId(request.getTmdbId());
        party.setProviderId(request.getProviderId());
        party.setProgress(0.0);
        party.setSeasonNo(request.getSeasonNo());
        party.setEpisodeNo(request.getEpisodeNo());
        party.setStatus(PartyStatus.ACTIVE);
        party.setParticipants(new ArrayList<>(List.of(host)));
        party.setCreatedAt(Instant.now());
        validateModel(party);

        long ttl = RedisConfig.PARTY_TTL_SECONDS;
        saveParty(party, ttl);
        redisTemplate.opsForValue().set(userActivePartyKey(userId), partyId, ttl, TimeUnit.SECONDS);

        publishUserJoined(partyId, host);
        log.info("Party created: partyId={}, host={}", partyId, userId);

        return buildPartyStateResponse(party, buildStreamUrl(party), Collections.emptyList());
    }

    // Adds the caller to an existing party; returns snapshot with built streamUrl
    // at current progress
    @Override
    public PartyStateResponse joinParty(JoinPartyRequest request, UUID userId, String username, String avatarUrl) {
        String partyId = request.getPartyId().toUpperCase();

        String existingPartyId = (String) redisTemplate.opsForValue().get(userActivePartyKey(userId));
        if (existingPartyId != null) {
            try {
                leaveParty(existingPartyId, userId);
            } catch (Exception e) {
                redisTemplate.delete(userActivePartyKey(userId));
                log.warn("Failed to cleanly leave old party {} for user {}, proceeding to join new", existingPartyId,
                        userId);
            }
        }

        ReentrantLock lock = getPartyLock(partyId);
        lock.lock();
        try {
            Party party = getActiveParty(partyId);

            if (party.getParticipants().size() >= MAX_PARTICIPANTS)
                throw new IllegalStateException("Party is full (max " + MAX_PARTICIPANTS + " participants).");
            if (party.getParticipants().stream().anyMatch(p -> p.getUserId().equals(userId))) {
                // If they are actually still in the list, just return the state (idempotent
                // join)
                return buildPartyStateResponse(party, buildStreamUrl(party), getRecentMessages(partyId, 50));
            }

            PartyParticipants participant = buildParticipant(userId, username, avatarUrl, false);
            party.getParticipants().add(participant);
            validateModel(party);

            long ttl = RedisConfig.PARTY_TTL_SECONDS;
            saveParty(party, ttl);
            redisTemplate.opsForValue().set(userActivePartyKey(userId), partyId, ttl, TimeUnit.SECONDS);
            publishUserJoined(partyId, participant);
            log.info("User {} joined party {}", userId, partyId);

            // Build streamUrl at current progress so joiner can start immediately at the
            // right position
            return buildPartyStateResponse(party, buildStreamUrl(party), getRecentMessages(partyId, 50));
        } finally {
            lock.unlock();
        }
    }

    // Removes the caller from the party; triggers host migration or end if no
    // participants remain
    @Override
    public void leaveParty(String partyId, UUID userId) {
        ReentrantLock lock = getPartyLock(partyId);
        lock.lock();
        try {
            Party party = getActiveParty(partyId);
            boolean wasHost = party.getHostId().equals(userId);

            // Always clean up the user's active party key first so they don't get stuck
            redisTemplate.delete(userActivePartyKey(userId));

            if (!party.getParticipants().removeIf(p -> p.getUserId().equals(userId))) {
                // They are already not in the participant list, just return smoothly
                return;
            }

            if (party.getParticipants().isEmpty()) {
                endParty(party);
            } else if (wasHost) {
                migrateHost(party);
                saveParty(party, RedisConfig.PARTY_TTL_SECONDS);
                publishUserLeft(partyId, userId);
            } else {
                saveParty(party, RedisConfig.PARTY_TTL_SECONDS);
                publishUserLeft(partyId, userId);
            }
            log.info("User {} left party {}", userId, partyId);
        } finally {
            lock.unlock();
        }
    }

    // Stores the host's current progress and providerId; URL is NOT built here —
    // only on join/sync
    @Override
    public void pushProgress(PartyProgressRequest request, UUID callerId) {
        String partyId = request.getPartyId();
        Party party = getActiveParty(partyId);

        if (!party.getHostId().equals(callerId))
            throw new AccessDeniedException("Only the host can update party progress.");

        party.setProgress(request.getProgress());
        party.setProviderId(request.getProviderId());
        saveParty(party, RedisConfig.PARTY_TTL_SECONDS);

        // Broadcast lightweight update — no URL; clients use the sync endpoint for
        // playback
        eventPublisher.publishEvent(partyId, PartyEventType.PARTY_STATE_UPDATED, callerId,
                Map.of("progress", request.getProgress(), "providerId", request.getProviderId()));
    }

    // Updates lastHeartbeat for the participant, preventing zombie eviction
    @Override
    public void sendHeartbeat(String partyId, UUID userId) {
        ReentrantLock lock = getPartyLock(partyId);
        lock.lock();
        try {
            Party party = getActiveParty(partyId);
            party.getParticipants().stream()
                    .filter(p -> p.getUserId().equals(userId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("User is not a participant in this party."))
                    .setLastHeartbeat(Instant.now());
            saveParty(party, RedisConfig.PARTY_TTL_SECONDS);

            // Also publish a HEARTBEAT event so the frontend event log can display it
            eventPublisher.publishEvent(partyId, PartyEventType.HEARTBEAT, userId,
                    Map.of("userId", userId.toString()));
        } finally {
            lock.unlock();
        }
    }

    // Stores the message in Redis and broadcasts it via NEW_CHAT SSE event to all
    // party members
    @Override
    public void sendChat(SendChatRequest request, UUID senderId, String senderName, String avatarUrl) {
        String partyId = request.getPartyId();
        Party party = getActiveParty(partyId);

        party.getParticipants().stream()
                .filter(p -> p.getUserId().equals(senderId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("User is not a participant in this party."));

        Messages message = new Messages();
        message.setId(UUID.randomUUID());
        message.setSenderId(senderId);
        message.setSenderName(senderName);
        message.setAvatarUrl(avatarUrl);
        message.setContent(request.getContent());
        message.setSentAt(Instant.now());
        validateModel(message);

        try {
            String msgKey = messagesKey(partyId);
            redisTemplate.opsForList().rightPush(msgKey, objectMapper.writeValueAsString(message));
            redisTemplate.opsForList().trim(msgKey, -MAX_CHAT_MESSAGES, -1);
            redisTemplate.expire(msgKey, RedisConfig.PARTY_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to store chat message for party {}", partyId, e);
            throw new RuntimeException("Failed to store message.", e);
        }

        // NEW_CHAT event carries the full message — frontend appends it directly to
        // chat UI
        eventPublisher.publishEvent(partyId, PartyEventType.NEW_CHAT, senderId, message);
    }

    // Builds the stream URL at current progress and returns it for immediate player
    // load (no SSE event fired)
    @Override
    public SyncResponse sync(String partyId, UUID userId) {
        Party party = getActiveParty(partyId);
        party.getParticipants().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("User is not a participant in this party."));
        return new SyncResponse(party.getProgress(), party.getProviderId(), buildStreamUrl(party));
    }

    // Returns full party metadata and last 50 messages; does not build a streamUrl
    @Override
    public PartyStateResponse getPartyState(String partyId, UUID userId) {
        Party party = getActiveParty(partyId);
        party.getParticipants().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("User is not a participant in this party."));
        String streamUrl = null;
        try {
            streamUrl = buildStreamUrl(party);
        } catch (Exception e) {
            log.warn("Failed to build streamUrl for party state response: {}", e.getMessage());
        }

        return buildPartyStateResponse(party, streamUrl, getRecentMessages(partyId, 50));
    }

    // Loads and deserializes the party from Redis; throws if not found or already
    // ended
    public Party getActiveParty(String partyId) {
        Object raw = redisTemplate.opsForValue().get(partyKey(partyId));
        if (raw == null)
            throw new NoSuchElementException("Party not found or has expired.");
        Party party = objectMapper.convertValue(raw, Party.class);
        if (party.getStatus() == PartyStatus.ENDED)
            throw new NoSuchElementException("This party has ended.");
        return party;
    }

    // Serializes and saves the party blob to Redis with the given TTL
    public void saveParty(Party party, long ttlSeconds) {
        redisTemplate.opsForValue().set(partyKey(party.getPartyId()), party, ttlSeconds, TimeUnit.SECONDS);
    }

    // Marks party as ENDED, sets 5-min TTL, publishes PARTY_ENDED event, cleans up
    // lock
    public void endParty(Party party) {
        party.setStatus(PartyStatus.ENDED);
        party.setEndedAt(Instant.now());
        long endedTtl = RedisConfig.PARTY_ENDED_TTL_SECONDS;
        saveParty(party, endedTtl);
        eventPublisher.publishEvent(party.getPartyId(), PartyEventType.PARTY_ENDED, party.getHostId(),
                Map.of("endedAt", party.getEndedAt().toString()));
        partyLocks.remove(party.getPartyId());
        log.info("Party ended: partyId={}", party.getPartyId());
    }

    // Promotes the earliest-joined non-host participant to host and publishes
    // HOST_MIGRATED event
    public void migrateHost(Party party) {
        PartyParticipants newHost = party.getParticipants().stream()
                .filter(p -> !p.isHost())
                .min(Comparator.comparing(PartyParticipants::getJoinedAt))
                .orElse(null);
        if (newHost == null)
            return;

        party.getParticipants().forEach(p -> p.setHost(false));
        newHost.setHost(true);
        party.setHostId(newHost.getUserId());

        eventPublisher.publishEvent(party.getPartyId(), PartyEventType.HOST_MIGRATED, newHost.getUserId(),
                Map.of("newHostId", newHost.getUserId().toString(), "newHostName", newHost.getUsername()));
        log.info("Host migrated: partyId={}, newHost={}", party.getPartyId(), newHost.getUserId());
    }

    // Fetches up to `count` recent chat messages from Redis list; deserializes each
    // JSON string
    public List<Messages> getRecentMessages(String partyId, int count) {
        List<Object> raw = redisTemplate.opsForList().range(messagesKey(partyId), -count, -1);
        if (raw == null)
            return Collections.emptyList();
        return raw.stream()
                .map(r -> {
                    try {
                        return objectMapper.readValue(r.toString(), Messages.class);
                    } catch (Exception e) {
                        log.warn("Failed to deserialize message", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // Constructs the stream URL using StreamUrlService with startAt = current
    // progress (truncated to seconds)
    private String buildStreamUrl(Party party) {
        StreamProviderRequest req = new StreamProviderRequest();
        req.setTmdbId(party.getTmdbId());
        req.setSeasonNo(party.getSeasonNo());
        req.setEpisodeNo(party.getEpisodeNo());
        req.setStartAt((int) party.getProgress());

        return streamUrlService.buildStreamUrls(req, party.getMediaType(), null)
                .stream()
                .filter(u -> u.getId().equals(party.getProviderId()))
                .map(u -> u.getUrl())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Provider '" + party.getProviderId() + "' not found or inactive."));
    }

    // Validates that TV/Anime content has positive season and episode numbers
    private void validateTvFields(MediaType mediaType, int seasonNo, int episodeNo) {
        if (mediaType != MediaType.Movie && (seasonNo <= 0 || episodeNo <= 0))
            throw new IllegalArgumentException("Season and episode number are required for TV/Anime content.");
    }

    // Runs Jakarta Bean Validation on a model; throws with all violation messages
    // if any fail
    private <T> void validateModel(T model) {
        Set<ConstraintViolation<T>> violations = validator.validate(model);
        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Model validation failed: " + errors);
        }
    }

    // Generates a unique 8-char alphanumeric party code using SETNX atomicity to
    // prevent collisions
    private String generateUniquePartyCode() {
        for (int i = 0; i < PARTY_CODE_MAX_RETRIES; i++) {
            String code = generateCode(8);
            // SETNX: set only if not exists — collision-safe
            Boolean absent = redisTemplate.opsForValue().setIfAbsent(
                    partyKey(code), "RESERVED", RedisConfig.PARTY_TTL_SECONDS, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(absent))
                return code;
        }
        throw new RuntimeException(
                "Could not generate unique party code after " + PARTY_CODE_MAX_RETRIES + " attempts.");
    }

    // Generates a random alphanumeric string of the given length using a
    // cryptographically secure source
    private String generateCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++)
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        return sb.toString();
    }

    // Builds a PartyParticipants record with current timestamps
    private PartyParticipants buildParticipant(UUID userId, String username, String avatarUrl, boolean isHost) {
        PartyParticipants p = new PartyParticipants();
        p.setUserId(userId);
        p.setUsername(username);
        p.setAvatarUrl(avatarUrl);
        p.setHost(isHost);
        p.setJoinedAt(Instant.now());
        p.setLastHeartbeat(Instant.now());
        return p;
    }

    // Publishes a USER_JOINED SSE event with the participant's display info
    private void publishUserJoined(String partyId, PartyParticipants participant) {
        eventPublisher.publishEvent(partyId, PartyEventType.USER_JOINED, participant.getUserId(), Map.of(
                "userId", participant.getUserId().toString(),
                "username", participant.getUsername(),
                "avatarUrl", Optional.ofNullable(participant.getAvatarUrl()).orElse("")));
    }

    // Publishes a USER_LEFT SSE event identifying the departing user
    private void publishUserLeft(String partyId, UUID userId) {
        eventPublisher.publishEvent(partyId, PartyEventType.USER_LEFT, userId, Map.of("userId", userId.toString()));
    }

    // Assembles the full PartyStateResponse; streamUrl may be null for
    // metadata-only responses
    private PartyStateResponse buildPartyStateResponse(Party party, String streamUrl, List<Messages> messages) {
        return PartyStateResponse.builder()
                .partyId(party.getPartyId())
                .hostId(party.getHostId())
                .mediaType(party.getMediaType())
                .tmdbId(party.getTmdbId())
                .seasonNo(party.getSeasonNo())
                .episodeNo(party.getEpisodeNo())
                .providerId(party.getProviderId())
                .streamUrl(streamUrl)
                .progress(party.getProgress())
                .status(party.getStatus())
                .createdAt(party.getCreatedAt())
                .participants(party.getParticipants())
                .recentMessages(messages)
                .build();
    }

    // Returns or creates the per-party lock used to serialize participant list
    // mutations
    public ReentrantLock getPartyLock(String partyId) {
        return partyLocks.computeIfAbsent(partyId, id -> new ReentrantLock());
    }
}
