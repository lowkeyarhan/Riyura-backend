package com.riyura.backend.common.util;

import java.util.Map;

public final class LanguageMapper {

    private LanguageMapper() {
    }

    private static final Map<String, String> LANGUAGE_CODES = Map.ofEntries(
            Map.entry("english", "en"),
            Map.entry("french", "fr"),
            Map.entry("german", "de"),
            Map.entry("spanish", "es"),
            Map.entry("italian", "it"),
            Map.entry("portuguese", "pt"),
            Map.entry("japanese", "ja"),
            Map.entry("korean", "ko"),
            Map.entry("chinese", "zh"),
            Map.entry("mandarin", "zh"),
            Map.entry("hindi", "hi"),
            Map.entry("arabic", "ar"),
            Map.entry("russian", "ru"),
            Map.entry("turkish", "tr"),
            Map.entry("thai", "th"),
            Map.entry("dutch", "nl"),
            Map.entry("polish", "pl"),
            Map.entry("swedish", "sv"),
            Map.entry("danish", "da"),
            Map.entry("norwegian", "no"),
            Map.entry("finnish", "fi"),
            Map.entry("greek", "el"),
            Map.entry("hebrew", "he"),
            Map.entry("indonesian", "id"),
            Map.entry("malay", "ms"),
            Map.entry("tagalog", "tl"),
            Map.entry("vietnamese", "vi"),
            Map.entry("czech", "cs"),
            Map.entry("romanian", "ro"),
            Map.entry("hungarian", "hu"),
            Map.entry("ukrainian", "uk"));

    public static String toIsoCode(String language) {
        if (language == null || language.isBlank())
            return null;

        String trimmed = language.trim();

        // Already a 2-letter ISO code — pass through directly
        if (trimmed.length() == 2)
            return trimmed.toLowerCase();

        return LANGUAGE_CODES.get(trimmed.toLowerCase());
    }
}
