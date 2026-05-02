package com.riyura.backend.modules.identity.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.riyura.backend.common.model.MediaType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "watchlist", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "user_id", "tmdb_id", "media_type" })
}, indexes = {
        @Index(name = "idx_watchlist_user_id", columnList = "user_id"),
        @Index(name = "idx_watchlist_user_added", columnList = "user_id, added_at DESC")
})
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Watchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "tmdb_id", nullable = false)
    private Long tmdbId;

    @Column(nullable = false)
    private String title;

    @Column(name = "media_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private MediaType mediaType;

    @Column(name = "poster_path")
    private String posterPath;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "vote")
    private BigDecimal vote;

    @Column(name = "added_at")
    private OffsetDateTime addedAt;

    @Column(name = "number_of_seasons")
    private Integer numberOfSeasons;

    @Column(name = "number_of_episodes")
    private Integer numberOfEpisodes;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Watchlist w))
            return false;
        return id != null && id.equals(w.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
