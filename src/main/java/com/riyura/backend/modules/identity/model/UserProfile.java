package com.riyura.backend.modules.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "profiles")
public class UserProfile {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "display_name")
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "last_login")
    private OffsetDateTime lastLogin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "onboarded")
    private Boolean onboarded = false;
}
