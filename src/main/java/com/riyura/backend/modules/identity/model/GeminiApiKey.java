package com.riyura.backend.modules.identity.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "gemini_api_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeminiApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "encrypted_key", nullable = false, columnDefinition = "text")
    private String encryptedKey;

    @Column(name = "iv", nullable = false, columnDefinition = "text")
    private String iv;

    @Column(name = "auth_tag", nullable = false, columnDefinition = "text")
    private String authTag;

    @Column(name = "key_preview", nullable = false, columnDefinition = "text")
    private String keyPreview;

    @Column(name = "key_version")
    @Builder.Default
    private Integer keyVersion = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}