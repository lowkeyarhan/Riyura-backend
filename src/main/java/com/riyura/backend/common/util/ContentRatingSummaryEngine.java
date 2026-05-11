package com.riyura.backend.common.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ContentRatingSummaryEngine {

    public record RatingEntry(String country, String rating) {
    }

    public record RatingSummary(String summaryRating, double confidence, List<String> reasoning) {
    }

    enum Level {
        U("U", 0),
        PG("PG", 1),
        UA("UA", 2),
        A("A", 3),
        EXPLICIT("18+", 4);

        private final String label;
        private final int severity;

        Level(String label, int severity) {
            this.label = label;
            this.severity = severity;
        }

        Level bump() {
            return switch (this) {
                case U -> PG;
                case PG -> UA;
                case UA -> A;
                case A, EXPLICIT -> EXPLICIT;
            };
        }

        boolean moreRestrictiveThan(Level other) {
            return other == null || this.severity > other.severity;
        }
    }

    private static final Pattern AGE_PATTERN = Pattern.compile("\\d+");
    private static final List<String> INTENSE_GENRES = List.of(
            "horror",
            "thriller",
            "crime",
            "war",
            "mystery",
            "fantasy",
            "sci-fi",
            "science fiction");
    private static final List<String> TEEN_THEMES = List.of(
            "teen",
            "high school",
            "supernatural",
            "vampire");
    private static final List<String> EXPLICIT_KEYWORDS = List.of(
            "nudity",
            "sexual",
            "sex",
            "rape",
            "gore",
            "torture",
            "drug",
            "abuse",
            "explicit");
    private static final List<String> EXTREME_KEYWORDS = List.of(
            "porn",
            "pornography",
            "hardcore",
            "explicit sex",
            "graphic sexual");

    private ContentRatingSummaryEngine() {
    }

    public static String summarizeLabel(List<RatingEntry> ratings, List<? extends GenreLike> genres, String overview) {
        return summarize(ratings, genres, overview).summaryRating();
    }

    public static RatingSummary summarize(List<RatingEntry> ratings, List<? extends GenreLike> genres,
            String overview) {
        List<RatingEntry> safeRatings = ratings == null ? List.of() : ratings;
        List<Level> levels = safeRatings.stream()
                .map(entry -> mapToLevel(entry.country(), entry.rating()))
                .filter(Objects::nonNull)
                .toList();

        List<String> reasoning = new ArrayList<>();
        Level baseLevel = determineBaseLevel(levels, reasoning, genres, overview);
        Level adjustedLevel = adjustForContent(baseLevel, genres, overview, reasoning);

        double confidence = confidenceFromLevels(levels, baseLevel, adjustedLevel);

        return new RatingSummary(adjustedLevel.label, confidence, reasoning);
    }

    static Level mapToLevel(String country, String rating) {
        if (rating == null) {
            return null;
        }
        String value = rating.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty() || "NR".equals(value) || "UNRATED".equals(value) || "NOT RATED".equals(value)) {
            return null;
        }

        String compact = value.replaceAll("[\\s-]", "");

        Level countryOverride = countryOverride(country, compact);
        if (countryOverride != null) {
            return countryOverride;
        }

        if (compact.contains("NC17") || compact.contains("TVMA") || compact.contains("R18")
                || compact.contains("R21") || compact.contains("X18") || "X".equals(compact)
                || "XXX".equals(compact) || compact.contains("18+")) {
            return Level.EXPLICIT;
        }

        if ("R".equals(compact)) {
            return Level.A;
        }

        if ("M".equals(compact)) {
            return Level.UA;
        }

        Integer age = extractAge(compact);
        if (age != null) {
            if (age >= 18) {
                return Level.EXPLICIT;
            }
            if (age >= 17) {
                return Level.A;
            }
            if (age >= 13) {
                return Level.UA;
            }
            if (age >= 8) {
                return Level.PG;
            }
            return Level.U;
        }

        if (value.contains("PG")) {
            return Level.PG;
        }

        if (value.contains("G") || value.contains("U") || value.contains("ALL")) {
            return Level.U;
        }

        return null;
    }

    private static Level countryOverride(String country, String compactRating) {
        if (country == null || compactRating == null) {
            return null;
        }
        String code = country.trim().toUpperCase(Locale.ROOT);
        return switch (code) {
            case "IN" -> {
                if ("A".equals(compactRating)) {
                    yield Level.A;
                }
                if (compactRating.startsWith("UA") || compactRating.startsWith("U/A")) {
                    yield Level.UA;
                }
                if ("U".equals(compactRating)) {
                    yield Level.U;
                }
                yield null;
            }
            case "US" -> {
                if ("TVMA".equals(compactRating) || "NC17".equals(compactRating)) {
                    yield Level.EXPLICIT;
                }
                if ("R".equals(compactRating)) {
                    yield Level.A;
                }
                if ("PG13".equals(compactRating) || "TV14".equals(compactRating)) {
                    yield Level.UA;
                }
                if ("TVPG".equals(compactRating) || "PG".equals(compactRating)) {
                    yield Level.PG;
                }
                if ("TVG".equals(compactRating) || "TVY".equals(compactRating) || "G".equals(compactRating)) {
                    yield Level.U;
                }
                yield null;
            }
            default -> null;
        };
    }

    private static Integer extractAge(String value) {
        Matcher matcher = AGE_PATTERN.matcher(value);
        Integer max = null;
        while (matcher.find()) {
            int number = Integer.parseInt(matcher.group());
            if (max == null || number > max) {
                max = number;
            }
        }
        return max;
    }

    private static Level determineBaseLevel(List<Level> levels, List<String> reasoning,
            List<? extends GenreLike> genres, String overview) {
        if (levels.isEmpty()) {
            Level inferred = inferFromGenresAndOverview(genres, overview, reasoning);
            reasoning.add("No reliable ratings available; inferred from genres and overview.");
            return inferred;
        }

        Map<Level, Long> counts = levels.stream()
                .collect(Collectors.groupingBy(level -> level, Collectors.counting()));
        long maxCount = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<Level> topLevels = counts.entrySet().stream()
                .filter(entry -> entry.getValue() == maxCount)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparingInt(level -> -level.severity))
                .toList();

        Level chosen = topLevels.get(0);
        if (topLevels.size() > 1) {
            reasoning.add("Ratings are split; choosing the stricter level.");
        }
        reasoning.add("Majority ratings map to " + chosen.label + ".");
        return chosen;
    }

    private static Level inferFromGenresAndOverview(List<? extends GenreLike> genres, String overview,
            List<String> reasoning) {
        boolean hasExplicit = containsAny(overview, EXPLICIT_KEYWORDS);
        boolean hasExtreme = containsAny(overview, EXTREME_KEYWORDS);
        boolean hasIntenseGenre = hasGenreMatch(genres, INTENSE_GENRES);

        if (hasExtreme) {
            reasoning.add("Overview indicates explicit adult content.");
            return Level.EXPLICIT;
        }

        if (hasExplicit) {
            reasoning.add("Overview indicates mature themes.");
            return Level.A;
        }

        if (hasIntenseGenre) {
            reasoning.add("Genres suggest intense or fantasy violence.");
            return Level.UA;
        }

        if (containsAny(overview, TEEN_THEMES)) {
            reasoning.add("Teen-oriented themes suggest UA.");
            return Level.UA;
        }

        return Level.PG;
    }

    private static Level adjustForContent(Level base, List<? extends GenreLike> genres, String overview,
            List<String> reasoning) {
        Level result = base;

        boolean hasTeenFantasy = hasGenreMatch(genres, List.of("fantasy", "sci-fi", "science fiction"));
        if (hasTeenFantasy && result.severity < Level.UA.severity) {
            result = Level.UA;
            reasoning.add("Fantasy or sci-fi themes raise the rating to UA.");
        }

        if (containsAny(overview, EXTREME_KEYWORDS)) {
            reasoning.add("Explicit content pushes rating to 18+.");
            return Level.EXPLICIT;
        }

        if (containsAny(overview, EXPLICIT_KEYWORDS)) {
            reasoning.add("Mature content increases the rating.");
            return result.bump();
        }

        if (hasGenreMatch(genres, INTENSE_GENRES)) {
            reasoning.add("Intense genre themes increase the rating.");
            return result.bump();
        }

        return result;
    }

    private static double confidenceFromLevels(List<Level> levels, Level baseLevel, Level adjustedLevel) {
        if (levels.isEmpty()) {
            return 0.45;
        }

        Map<Level, Long> counts = levels.stream()
                .collect(Collectors.groupingBy(level -> level, Collectors.counting()));
        long maxCount = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        double confidence = (double) maxCount / levels.size();

        if (adjustedLevel != baseLevel) {
            confidence = Math.max(0.4, confidence - 0.1);
        }

        return Math.round(confidence * 100.0) / 100.0;
    }

    private static boolean hasGenreMatch(List<? extends GenreLike> genres, List<String> targets) {
        if (genres == null || genres.isEmpty()) {
            return false;
        }
        for (GenreLike genre : genres) {
            if (genre == null || genre.getName() == null) {
                continue;
            }
            String name = genre.getName().toLowerCase(Locale.ROOT);
            for (String target : targets) {
                if (name.contains(target)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsAny(String text, List<String> targets) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String target : targets) {
            if (lower.contains(target)) {
                return true;
            }
        }
        return false;
    }
}
