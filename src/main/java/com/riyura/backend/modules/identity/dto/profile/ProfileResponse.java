package com.riyura.backend.modules.identity.dto.profile;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfileResponse {
    private UUID id;
    private String name;
    private String email;
    private String photoUrl;
    private String lastLogin;
    private String createdAt;
    private boolean onboarded;
}
