package com.riyura.backend.modules.identity.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

import com.riyura.backend.common.converter.MediaTypeLowerCaseConverter;
import com.riyura.backend.common.model.MediaType;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "recommendations")
@IdClass(RecommendationId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recommendation implements Persistable<RecommendationId> {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "tmdb_id", nullable = false)
    private Long tmdbId;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Convert(converter = MediaTypeLowerCaseConverter.class)
    @Column(name = "media_type", nullable = false, columnDefinition = "text")
    private MediaType mediaType;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "number_of_seasons")
    private Integer numberOfSeasons;

    @Column(name = "number_of_episodes")
    private Integer numberOfEpisodes;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @CreationTimestamp
    @Column(name = "generated_at", updatable = false)
    private ZonedDateTime generatedAt;

    // Tells Spring Data this is always a new entity when created via the builder,
    // so save() calls em.persist() instead of a SELECT + em.merge(). @PostLoad
    // resets this to false for entities that were loaded from the DB.
    @Transient
    @Builder.Default
    private boolean isNew = true;

    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public RecommendationId getId() {
        return new RecommendationId(userId, tmdbId);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }
}
