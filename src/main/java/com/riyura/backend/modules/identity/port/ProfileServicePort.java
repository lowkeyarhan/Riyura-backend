package com.riyura.backend.modules.identity.port;

import com.riyura.backend.modules.identity.model.UserProfile;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public interface ProfileServicePort {
    UserProfile getProfile(Jwt jwt);

    UserProfile updateOnboarded(UUID userId, boolean onboarded);
}
