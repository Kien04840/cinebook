package com.cinebook.entity;

import com.cinebook.enums.ShowtimeFormat;
import com.cinebook.enums.ShowtimeStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
    name = "showtimes",
    indexes = {
        @Index(
            name = "idx_showtimes_movie_status_start",
            columnList = "movie_id, status, start_time"
        ),
        @Index(
            name = "idx_showtimes_auditorium_start",
            columnList = "auditorium_id, start_time"
        )
    }
)
public class Showtime {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "movie_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "FKshowtimes226731")
    )
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "auditorium_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "FKshowtimes189669")
    )
    private Auditorium auditorium;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ShowtimeFormat format;

    @Column(length = 20, nullable = false)
    private String language;

    @Column(length = 30)
    private String subtitle;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(
        name = "base_price",
        precision = 12,
        scale = 2,
        nullable = false
    )
    private BigDecimal basePrice;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ShowtimeStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "showtime")
    private List<Booking> bookings = new ArrayList<>();

    @OneToMany(mappedBy = "showtime")
    private List<SeatHold> seatHolds = new ArrayList<>();

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