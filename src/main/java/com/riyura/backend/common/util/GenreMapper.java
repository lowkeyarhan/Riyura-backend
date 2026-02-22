package com.riyura.backend.common.util;

import java.util.HashMap;
import java.util.Map;

public final class GenreMapper {

    private GenreMapper() {
    }

    // TMDB genre IDs for /discover/movie
    private static final Map<String, Integer> MOVIE_GENRES = Map.ofEntries(
            Map.entry("action", 28),
            Map.entry("adventure", 12),
            Map.entry("animation", 16),
            Map.entry("comedy", 35),
            Map.entry("crime", 80),
            Map.entry("documentary", 99),
            Map.entry("drama", 18),
            Map.entry("family", 10751),
            Map.entry("fantasy", 14),
            Map.entry("history", 36),
            Map.entry("horror", 27),
            Map.entry("music", 10402),
            Map.entry("mystery", 9648),
            Map.entry("romance", 10749),
            Map.entry("science fiction", 878),
            Map.entry("sci-fi", 878),
            Map.entry("thriller", 53),
            Map.entry("war", 10752),
            Map.entry("western", 37),
            Map.entry("tv movie", 10770));

    // TMDB genre IDs for /discover/tv
    private static final Map<String, Integer> TV_GENRES = Map.ofEntries(
            Map.entry("action", 10759),
            Map.entry("adventure", 10759),
            Map.entry("action & adventure", 10759),
            Map.entry("animation", 16),
            Map.entry("comedy", 35),
            Map.entry("crime", 80),
            Map.entry("documentary", 99),
            Map.entry("drama", 18),
            Map.entry("family", 10751),
            Map.entry("fantasy", 10765),
            Map.entry("kids", 10762),
            Map.entry("mystery", 9648),
            Map.entry("news", 10763),
            Map.entry("reality", 10764),
            Map.entry("romance", 10749),
            Map.entry("science fiction", 10765),
            Map.entry("sci-fi", 10765),
            Map.entry("sci-fi & fantasy", 10765),
            Map.entry("soap", 10766),
            Map.entry("talk", 10767),
            Map.entry("war", 10768),
            Map.entry("war & politics", 10768),
            Map.entry("western", 37));

    // Reverse map for ID -> Name lookup
    private static final Map<Integer, String> ID_TO_NAME = new HashMap<>();

    static {

        for (Map.Entry<String, Integer> entry : MOVIE_GENRES.entrySet()) {
            ID_TO_NAME.putIfAbsent(entry.getValue(), capitalize(entry.getKey()));
        }
        for (Map.Entry<String, Integer> entry : TV_GENRES.entrySet()) {
            ID_TO_NAME.putIfAbsent(entry.getValue(), capitalize(entry.getKey()));
        }

        ID_TO_NAME.put(878, "Science Fiction");
        ID_TO_NAME.put(10765, "Sci-Fi & Fantasy");
        ID_TO_NAME.put(10759, "Action & Adventure");
        ID_TO_NAME.put(10768, "War & Politics");
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty())
            return text;
        String[] words = text.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }
        return sb.toString().trim();
    }

    public static String toMovieGenreIds(String genreNames) {
        return toIds(genreNames, MOVIE_GENRES);
    }

    public static String toTvGenreIds(String genreNames) {
        return toIds(genreNames, TV_GENRES);
    }

    public static String getGenreName(Integer id) {
        return id != null ? ID_TO_NAME.get(id) : null;
    }

    private static String toIds(String genreNames, Map<String, Integer> map) {
        if (genreNames == null || genreNames.isBlank())
            return null;

        String ids = java.util.Arrays.stream(genreNames.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .map(map::get)
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));

        return ids.isBlank() ? null : ids;
    }
}
