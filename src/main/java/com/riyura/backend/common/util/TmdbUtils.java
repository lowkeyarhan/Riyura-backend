package com.riyura.backend.common.util;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

public final class TmdbUtils {

    private TmdbUtils() {
    }

    public static String extractYear(String dateString) {
        if (dateString == null || dateString.isEmpty())
            return null;
        try {
            return String.valueOf(LocalDate.parse(dateString).getYear());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static boolean isJapanese(String lang) {
        return "ja".equalsIgnoreCase(lang);
    }

    public static boolean hasAnimationGenre(List<? extends GenreLike> genres) {
        if (genres == null || genres.isEmpty())
            return false;
        return genres.stream()
                .anyMatch(
                        g -> g != null && ("Animation".equals(g.getName()) || (g.getId() != null && g.getId() == 16)));
    }

    public static boolean isAnime(String originalLanguage, List<? extends GenreLike> genres) {
        return isJapanese(originalLanguage) && hasAnimationGenre(genres);
    }
}
