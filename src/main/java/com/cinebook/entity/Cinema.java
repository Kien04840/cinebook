package com.cinebook.entity;

import com.cinebook.enums.CinemaStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
    name = "cinemas",
    indexes = {
        @Index(name = "idx_cinemas_status", columnList = "status"),
        @Index(name = "idx_cinemas_city", columnList = "city")
    }
)
public class Cinema {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(length = 255, nullable = false)
    private String name;

    @Column(length = 500, nullable = false)
    private String address;

    @Column(length = 100, nullable = false)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private CinemaStatus status;

    @Column(name = "opening_time", nullable = false)
    private java.time.LocalTime openingTime;

    @Column(name = "closing_time", nullable = false)
    private java.time.LocalTime closingTime;

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
        mappedBy = "cinema",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private Set<Auditorium> auditoriums = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }

        LocalDateTime now = LocalDateTime.now();

        if (openingTime == null) {
            openingTime = java.time.LocalTime.of(8, 0);
        }

        if (closingTime == null) {
            closingTime = java.time.LocalTime.of(23, 0);
        }

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