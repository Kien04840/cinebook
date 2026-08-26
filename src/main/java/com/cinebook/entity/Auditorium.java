package com.cinebook.entity;

import com.cinebook.enums.AuditoriumStatus;
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
    name = "auditoriums",
    indexes = {
        @Index(name = "idx_auditoriums_cinema_id", columnList = "cinema_id"),
        @Index(name = "idx_auditoriums_status", columnList = "status")
    }
)
public class Auditorium {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "cinema_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "FKauditorium794462")
    )
    private Cinema cinema;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 20, nullable = false)
    private String type;

    @Column(name = "rows_count", nullable = false)
    private Short rowsCount;

    @Column(name = "columns_count", nullable = false)
    private Short columnsCount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private AuditoriumStatus status;

    @Column(name = "turnaround_minutes", nullable = false)
    private Short turnaroundMinutes;

    @Column(name = "snap_interval_minutes", nullable = false)
    private Short snapIntervalMinutes;

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
        mappedBy = "auditorium",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private Set<Seat> seats = new HashSet<>();

    @OneToMany(mappedBy = "auditorium")
    private Set<Showtime> showtimes = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }

        LocalDateTime now = LocalDateTime.now();

        if (turnaroundMinutes == null) {
            turnaroundMinutes = (short) 15;
        }

        if (snapIntervalMinutes == null) {
            snapIntervalMinutes = (short) 15;
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