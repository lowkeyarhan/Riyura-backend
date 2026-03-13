package com.riyura.backend.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class EncryptionUtils {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // in bits (16 bytes)
    private static final int IV_LENGTH = 16; // in bytes

    private final SecretKeySpec secretKey;

    public EncryptionUtils(@Value("${riyura.security.encryption-key}") String hexKey) {
        if (hexKey == null || hexKey.length() != 64) {
            throw new IllegalArgumentException("Invalid ENCRYPTION_KEY: Must be a 64-character hex string.");
        }
        byte[] keyBytes = HexFormat.of().parseHex(hexKey);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public EncryptedData encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherTextWithTag = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Java appends the auth tag to the end of the ciphertext. We split them.
            int cipherTextLength = cipherTextWithTag.length - (GCM_TAG_LENGTH / 8);
            byte[] cipherText = new byte[cipherTextLength];
            byte[] authTag = new byte[GCM_TAG_LENGTH / 8];

            System.arraycopy(cipherTextWithTag, 0, cipherText, 0, cipherTextLength);
            System.arraycopy(cipherTextWithTag, cipherTextLength, authTag, 0, authTag.length);

            return EncryptedData.builder()
                    .encryptedKey(Base64.getEncoder().encodeToString(cipherText))
                    .iv(Base64.getEncoder().encodeToString(iv))
                    .authTag(Base64.getEncoder().encodeToString(authTag))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedKeyB64, String ivB64, String authTagB64) {
        try {
            byte[] iv = Base64.getDecoder().decode(ivB64);
            byte[] encryptedKey = Base64.getDecoder().decode(encryptedKeyB64);
            byte[] authTag = Base64.getDecoder().decode(authTagB64);

            // Reconstruct the payload Java expects (ciphertext + auth tag)
            byte[] cipherTextWithTag = new byte[encryptedKey.length + authTag.length];
            System.arraycopy(encryptedKey, 0, cipherTextWithTag, 0, encryptedKey.length);
            System.arraycopy(authTag, 0, cipherTextWithTag, encryptedKey.length, authTag.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainTextBytes = cipher.doFinal(cipherTextWithTag);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class EncryptedData {
        private String encryptedKey;
        private String iv;
        private String authTag;
    }
}