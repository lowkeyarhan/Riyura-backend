package com.riyura.backend.modules.identity.port;

import java.util.Map;
import java.util.UUID;

public interface GeminiApiKeyServicePort {
    Map<String, Object> saveApiKey(UUID userId, String rawApiKey);

    Map<String, Object> getApiKeyStatus(UUID userId);

    void deleteApiKey(UUID userId);

    String getDecryptedKeyForUser(UUID userId);
}
