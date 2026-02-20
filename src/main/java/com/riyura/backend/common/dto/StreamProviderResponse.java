package com.riyura.backend.common.dto;

import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "stream_providers")
public class StreamProviderResponse {

    @Id
    @Column(name = "provider_id", unique = true, nullable = false)
    private String providerId;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(name = "movie_template")
    private String movieTemplate;

    @Column(name = "tv_template")
    private String tvTemplate;

    @Column(name = "anime_template")
    private String animeTemplate;

    @Column(nullable = false)
    private String quality;

    @Column(nullable = false)
    private Integer priority;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}