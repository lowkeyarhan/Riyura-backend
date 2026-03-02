package com.riyura.backend.modules.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.riyura.backend.modules.identity.model.UserProfile;
import com.riyura.backend.modules.identity.repository.UserProfileRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserProfileRepository userProfileRepository;

    // Fetch the user's profile along with creating it if it doesn't exist
    @Transactional
    public UserProfile getProfile(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());

        // Create the user profile if it doesn't exist
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseGet(() -> createProfile(userId, jwt));

        // Update the last login time
        profile.setLastLogin(OffsetDateTime.now());
        return userProfileRepository.save(profile);
    }

    // Update the user's onboarding status
    @Transactional
    public UserProfile updateOnboarded(UUID userId, boolean onboarded) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Profile not found for user: " + userId));

        profile.setOnboarded(onboarded);
        log.debug("Updated onboarded={} for user {}", onboarded, userId);
        return userProfileRepository.save(profile);
    }

    // Create a new user profile based on JWT claims
    private UserProfile createProfile(UUID userId, Jwt jwt) {
        log.info("Creating new profile for user {}", userId);

        Map<String, Object> userMetadata = jwt.getClaimAsMap("user_metadata");

        String name = extractString(userMetadata, "full_name", "name");
        String email = jwt.getClaimAsString("email");
        String photoUrl = extractString(userMetadata, "avatar_url", "picture");

        UserProfile profile = new UserProfile();
        profile.setId(userId);
        profile.setName(name);
        profile.setEmail(email != null ? email : "");
        profile.setPhotoUrl(photoUrl);
        profile.setCreatedAt(OffsetDateTime.now());
        profile.setOnboarded(false);

        return userProfileRepository.save(profile);
    }

    // Extract a non-blank string from a map given multiple possible keys
    private String extractString(Map<String, Object> map, String... keys) {
        if (map == null)
            return null;
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }
}
