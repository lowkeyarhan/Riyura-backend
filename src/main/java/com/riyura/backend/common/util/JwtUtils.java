package com.riyura.backend.common.util;

import org.springframework.security.oauth2.jwt.Jwt;
import java.util.Map;
import java.util.UUID;

public class JwtUtils {

    public static UUID extractUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public static String extractName(Jwt jwt) {
        Map<String, Object> metadata = jwt.getClaimAsMap("user_metadata");
        String name = extractFirstValidString(metadata, "full_name", "name", "username");
        if (name != null)
            return name;

        String email = jwt.getClaimAsString("email");
        return email != null ? email : "User";
    }

    public static String extractAvatarUrl(Jwt jwt) {
        Map<String, Object> metadata = jwt.getClaimAsMap("user_metadata");
        return extractFirstValidString(metadata, "avatar_url", "picture");
    }

    public static String extractEmail(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }

    private static String extractFirstValidString(Map<String, Object> map, String... keys) {
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
