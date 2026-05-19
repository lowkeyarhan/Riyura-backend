package com.riyura.backend.modules.watchalong.service.party;

import com.riyura.backend.modules.watchalong.model.enums.PartyEventType;
import com.riyura.backend.modules.watchalong.model.party.Party;
import com.riyura.backend.modules.watchalong.model.party.PartyParticipants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

// Scheduled sweeper that evicts zombie participants (missed heartbeats > 5 min) every 2 minutes
// Eviction order: migrate host if zombie → evict → end party if empty
@Slf4j
@Component
@RequiredArgsConstructor
public class PartyZombieSweeper {

    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofMinutes(5);

    private final RedisTemplate<String, Object> redisTemplate;
    private final PartyService partyService;
    private final PartyEventPublisher eventPublisher;

    // Entry point: scans all active party keys and inspects each for zombie
    // participants
    @Scheduled(fixedDelay = 120_000)
    public void sweep() {
        log.debug("Zombie sweeper running...");
        Set<String> keys = scanPartyKeys();
        if (keys.isEmpty())
            return;
        keys.forEach(key -> {
            try {
                sweepParty(key);
            } catch (Exception e) {
                log.error("Zombie sweep failed for key={}", key, e);
            }
        });
    }

    // Inspects a single party, collecting and evicting any participants whose
    // heartbeat has timed out
    private void sweepParty(String key) {
        Object raw = redisTemplate.opsForValue().get(key);
        if (raw == null)
            return;

        String partyId = extractPartyId(key);
        Party party;
        try {
            party = partyService.getActiveParty(partyId);
        } catch (NoSuchElementException e) {
            return;
        }

        Instant cutoff = Instant.now().minus(HEARTBEAT_TIMEOUT);
        List<UUID> zombies = party.getParticipants().stream()
                .filter(p -> p.getLastHeartbeat() != null && p.getLastHeartbeat().isBefore(cutoff))
                .map(PartyParticipants::getUserId)
                .toList();

        if (zombies.isEmpty())
            return;

        // Lock the party to safely mutate the participant list
        ReentrantLock lock = partyService.getPartyLock(partyId);
        lock.lock();
        try {
            // Re-read inside lock for freshest state
            try {
                party = partyService.getActiveParty(partyId);
            } catch (NoSuchElementException e) {
                return;
            }
            final Instant freshCutoff = Instant.now().minus(HEARTBEAT_TIMEOUT);

            for (UUID zombieId : zombies) {
                // Skip if heartbeat was refreshed between the two reads
                boolean stillZombie = party.getParticipants().stream()
                        .filter(p -> p.getUserId().equals(zombieId))
                        .anyMatch(p -> p.getLastHeartbeat() != null && p.getLastHeartbeat().isBefore(freshCutoff));
                if (!stillZombie)
                    continue;

                boolean isHost = party.getHostId().equals(zombieId);
                party.getParticipants().removeIf(p -> p.getUserId().equals(zombieId));
                redisTemplate.delete(PartyService.userActivePartyKey(zombieId));

                eventPublisher.publishEvent(partyId, PartyEventType.USER_EVICTED, zombieId,
                        Map.of("userId", zombieId.toString(), "reason", "HEARTBEAT_TIMEOUT"));
                log.info("Zombie evicted: partyId={}, userId={}", partyId, zombieId);

                if (party.getParticipants().isEmpty()) {
                    partyService.endParty(party);
                    return;
                }
                if (isHost)
                    partyService.migrateHost(party);
            }

            partyService.saveParty(party, com.riyura.backend.common.config.RedisConfig.PARTY_TTL_SECONDS);
        } finally {
            lock.unlock();
        }
    }

    // Scans Redis for keys matching the party:{8-char-code} pattern using SCAN
    // (never KEYS)
    private Set<String> scanPartyKeys() {
        Set<String> keys = new HashSet<>();
        try (var cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match("party:????????").count(100).build())) {
            cursor.forEachRemaining(key -> {
                if (key instanceof String s)
                    keys.add(s);
            });
        } catch (Exception e) {
            log.error("Failed to scan party keys", e);
        }
        return keys;
    }

    // Extracts the 8-char party code from the Redis key (format: party:{code})
    private String extractPartyId(String key) {
        return key.substring("party:".length());
    }
}
