package com.cinebook.entity;

import com.cinebook.enums.MovieStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
    name = "movies",
    indexes = {
        @Index(name = "idx_movies_title", columnList = "title"),
        @Index(name = "idx_movies_status", columnList = "status"),
        @Index(name = "idx_movies_release_date", columnList = "release_date")
    }
)
public class Movie {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "tmdb_id", unique = true)
    private Long tmdbId;

    @Column(length = 255, nullable = false)
    private String title;

    @Column(name = "original_title", length = 255)
    private String originalTitle;

    @Column(length = 2000, nullable = false)
    private String overview;

    @Column(name = "duration_minutes", nullable = false)
    private Short durationMinutes;

    @Column(length = 255, nullable = false)
    private String director;

    @Column(length = 1000, nullable = false)
    private String actors;

    @Column(length = 100)
    private String country;

    @Column(length = 100)
    private String language;

    @Column(name = "release_date", nullable = false)
    private LocalDate releaseDate;

    @Column(name = "age_rating", length = 10, nullable = false)
    private String ageRating;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @Column(name = "backdrop_url", length = 500)
    private String backdropUrl;

    @Column(name = "trailer_url", length = 500)
    private String trailerUrl;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private MovieStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(
        mappedBy = "movie",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private Set<MovieGenre> movieGenres = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }

        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }

        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}