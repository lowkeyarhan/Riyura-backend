package com.riyura.backend.modules.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.riyura.backend.modules.identity.model.UserProfile;
import com.riyura.backend.modules.identity.repository.UserProfileRepository;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.riyura.backend.common.util.JwtUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService implements com.riyura.backend.modules.identity.interfaces.ProfileServicePort {

    private final UserProfileRepository userProfileRepository;

    private static final int LAST_LOGIN_UPDATE_MINUTES = 5;

    // Fetch the user's profile along with creating it if it doesn't exist
    @Transactional
    public UserProfile getProfile(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());

        // Create the user profile if it doesn't exist
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseGet(() -> createProfile(userId, jwt));

        // Only update the last login time if it's been more than 5 minutes since the
        // last update
        if (profile.getLastLogin() == null
                || profile.getLastLogin().plusMinutes(LAST_LOGIN_UPDATE_MINUTES).isBefore(OffsetDateTime.now())) {
            profile.setLastLogin(OffsetDateTime.now());
            return userProfileRepository.save(profile);
        }

        return profile;
    }

    // Update the user's onboarding status
    @Transactional
    public UserProfile updateOnboarded(UUID userId, boolean onboarded) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));

        profile.setOnboarded(onboarded);
        log.debug("Updated onboarded={} for user {}", onboarded, userId);
        return userProfileRepository.save(profile);
    }

    // Create a new user profile based on JWT claims
    private UserProfile createProfile(UUID userId, Jwt jwt) {
        log.info("Creating new profile for user {}", userId);

        String name = JwtUtils.extractName(jwt);
        String email = JwtUtils.extractEmail(jwt);
        String photoUrl = JwtUtils.extractAvatarUrl(jwt);

        UserProfile profile = new UserProfile();
        profile.setId(userId);
        profile.setName(name);
        profile.setEmail(email != null ? email : "");
        profile.setPhotoUrl(photoUrl);
        profile.setCreatedAt(OffsetDateTime.now());
        profile.setOnboarded(false);

        // Save the profile and flush the changes
        try {
            return userProfileRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException e) {
            // Concurrent request already created the profile - fetch it
            log.debug("Profile already created concurrently for user {}", userId);
            return userProfileRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Failed to create profile"));
        }
    }
}
