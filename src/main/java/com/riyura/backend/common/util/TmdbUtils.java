package com.riyura.backend.common.util;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.riyura.backend.modules.content.dto.global.ContentRatingsResponse;
import com.riyura.backend.modules.content.dto.global.ReleaseDatesResponse;

public final class TmdbUtils {

    private TmdbUtils() {
    }

    public static final int GENRE_ANIMATION = 16;
    public static final int GENRE_TALK = 10767;
    public static final int GENRE_SOAP = 10766;

    public static String extractYear(String dateString) {
        if (dateString == null || dateString.isEmpty())
            return null;
        try {
            return String.valueOf(LocalDate.parse(dateString).getYear());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static boolean isJapanese(String lang) {
        return "ja".equalsIgnoreCase(lang);
    }

    // ── Core ID-based methods (single source of truth) ──

    public static boolean hasGenreId(List<Integer> genreIds, int targetGenreId) {
        return genreIds != null && genreIds.contains(targetGenreId);
    }

    public static boolean isAnimeByIds(String originalLanguage, List<Integer> genreIds) {
        return isJapanese(originalLanguage) && hasGenreId(genreIds, GENRE_ANIMATION);
    }

    public static boolean isTalkShow(List<Integer> genreIds) {
        return hasGenreId(genreIds, GENRE_TALK);
    }

    public static boolean isSoapOpera(List<Integer> genreIds) {
        return hasGenreId(genreIds, GENRE_SOAP);
    }

    // ── GenreLike adapter — extracts IDs and delegates ──

    private static List<Integer> extractIds(List<? extends GenreLike> genres) {
        if (genres == null)
            return List.of();
        return genres.stream()
                .filter(g -> g != null && g.getId() != null)
                .map(g -> g.getId().intValue())
                .toList();
    }

    public static boolean isAnime(String originalLanguage, List<? extends GenreLike> genres) {
        return isAnimeByIds(originalLanguage, extractIds(genres));
    }

    public static String getMovieMaturityRating(ReleaseDatesResponse releaseDates,
            List<? extends GenreLike> genres, String overview) {
        List<ContentRatingSummaryEngine.RatingEntry> ratings = new ArrayList<>();
        if (releaseDates != null && releaseDates.getResults() != null) {
            for (ReleaseDatesResponse.Result result : releaseDates.getResults()) {
                String certification = selectMostRestrictiveCertification(result.getIso_3166_1(),
                        result.getRelease_dates());
                if (certification != null && !certification.isBlank()) {
                    ratings.add(new ContentRatingSummaryEngine.RatingEntry(result.getIso_3166_1(), certification));
                }
            }
        }
        return ContentRatingSummaryEngine.summarizeLabel(ratings, genres, overview);
    }

    public static String getTvMaturityRating(ContentRatingsResponse contentRatings,
            List<? extends GenreLike> genres, String overview) {
        List<ContentRatingSummaryEngine.RatingEntry> ratings = new ArrayList<>();
        if (contentRatings != null && contentRatings.getResults() != null) {
            for (ContentRatingsResponse.Result result : contentRatings.getResults()) {
                if (result.getRating() != null && !result.getRating().isBlank()) {
                    ratings.add(new ContentRatingSummaryEngine.RatingEntry(result.getIso_3166_1(), result.getRating()));
                }
            }
        }
        return ContentRatingSummaryEngine.summarizeLabel(ratings, genres, overview);
    }

    private static String selectMostRestrictiveCertification(String country,
            List<ReleaseDatesResponse.ReleaseDate> releaseDates) {
        if (releaseDates == null || releaseDates.isEmpty()) {
            return null;
        }
        ContentRatingSummaryEngine.Level bestLevel = null;
        String bestRating = null;
        for (ReleaseDatesResponse.ReleaseDate releaseDate : releaseDates) {
            if (releaseDate == null || releaseDate.getCertification() == null) {
                continue;
            }
            String certification = releaseDate.getCertification();
            ContentRatingSummaryEngine.Level level = ContentRatingSummaryEngine.mapToLevel(country, certification);
            if (level == null) {
                continue;
            }
            if (level.moreRestrictiveThan(bestLevel)) {
                bestLevel = level;
                bestRating = certification;
            }
        }
        return bestRating;
    }
}
