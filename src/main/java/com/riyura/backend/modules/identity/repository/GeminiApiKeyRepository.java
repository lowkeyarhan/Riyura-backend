package com.riyura.backend.modules.identity.repository;

import com.riyura.backend.modules.identity.model.GeminiApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GeminiApiKeyRepository extends JpaRepository<GeminiApiKey, Long> {
    Optional<GeminiApiKey> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}