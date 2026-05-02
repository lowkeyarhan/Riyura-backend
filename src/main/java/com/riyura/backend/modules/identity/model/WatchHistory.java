package com.riyura.backend.modules.identity.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.riyura.backend.common.model.MediaType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "watch_history", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "user_id", "tmdb_id", "media_type" })
}, indexes = {
        @Index(name = "idx_watch_history_user_id", columnList = "user_id"),
        @Index(name = "idx_watch_history_user_watched", columnList = "user_id, watched_at DESC")
})
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class WatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tmdb_id", nullable = false)
    private Long tmdbId;

    @Column(nullable = false)
    private String title;

    @Column(name = "media_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private MediaType mediaType;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "backdrop_path")
    private String backdropPath;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "season_number")
    private Integer seasonNumber;

    @Column(name = "episode_number")
    private Integer episodeNumber;

    @Column(name = "episode_name")
    private String episodeName;

    @Column(name = "episode_length")
    private Integer episodeLength;

    @Column(name = "watched_at")
    private OffsetDateTime watchedAt;

    @Column(name = "is_anime")
    private Boolean isAnime;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof WatchHistory w))
            return false;
        return id != null && id.equals(w.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
