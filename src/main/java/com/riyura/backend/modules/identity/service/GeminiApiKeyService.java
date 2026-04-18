package com.riyura.backend.modules.identity.service;

import com.riyura.backend.common.util.EncryptionUtils;
import com.riyura.backend.modules.identity.model.GeminiApiKey;
import com.riyura.backend.modules.identity.repository.GeminiApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GeminiApiKeyService implements com.riyura.backend.modules.identity.port.GeminiApiKeyServicePort {

    private final GeminiApiKeyRepository repository;
    private final EncryptionUtils encryptionUtils;

    // Saves or updates the Gemini API key for a user. Returns the status and
    // preview of the key.
    @Transactional
    public Map<String, Object> saveApiKey(UUID userId, String rawApiKey) {
        if (!isValidGeminiApiKeyFormat(rawApiKey)) {
            throw new IllegalArgumentException("Invalid Gemini API Key format");
        }

        EncryptionUtils.EncryptedData encryptedData = encryptionUtils.encrypt(rawApiKey);
        String preview = getKeyPreview(rawApiKey);

        GeminiApiKey apiKey = repository.findByUserId(userId).orElse(new GeminiApiKey());
        apiKey.setUserId(userId);
        apiKey.setEncryptedKey(encryptedData.getEncryptedKey());
        apiKey.setIv(encryptedData.getIv());
        apiKey.setAuthTag(encryptedData.getAuthTag());
        apiKey.setKeyPreview(preview);

        repository.save(apiKey);

        // Return the status and preview to the client
        return Map.of(
                "hasKey", true,
                "keyPreview", preview);
    }

    // Retrieves the status of the API key for a user, including whether it exists
    // and a preview of the key.
    @Transactional(readOnly = true)
    public Map<String, Object> getApiKeyStatus(UUID userId) {
        Optional<GeminiApiKey> keyOpt = repository.findByUserId(userId);

        if (keyOpt.isPresent()) {
            return Map.of(
                    "hasKey", true,
                    "keyPreview", keyOpt.get().getKeyPreview());
        }

        return Map.of("hasKey", false, "keyPreview", null);
    }

    // Deletes the API key for a user.
    @Transactional
    public void deleteApiKey(UUID userId) {
        repository.deleteByUserId(userId);
    }

    // Retrieves and decrypts the API key for a user. Throws an exception if the key
    // is not found.
    @Transactional(readOnly = true)
    public String getDecryptedKeyForUser(UUID userId) {
        GeminiApiKey keyEntity = repository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("API Key not found for user"));

        return encryptionUtils.decrypt(keyEntity.getEncryptedKey(), keyEntity.getIv(), keyEntity.getAuthTag());
    }

    // Validates the format of the Gemini API key. This is a basic check and can be
    // enhanced based on actual key formats.
    private boolean isValidGeminiApiKeyFormat(String key) {
        return key != null && key.startsWith("AIza") && key.length() >= 20 && key.length() <= 100;
    }

    // Generates a preview of the API key for display purposes, showing only the
    // first 4 characters followed by asterisks.
    private String getKeyPreview(String key) {
        if (key == null || key.length() < 4)
            return "****";
        return key.substring(0, 4) + "***";
    }
}