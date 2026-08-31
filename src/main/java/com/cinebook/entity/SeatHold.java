package com.cinebook.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
    name = "seat_holds",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_seat_holds_showtime_seat",
            columnNames = {"showtime_id", "seat_id"}
        )
    },
    indexes = {
        @Index(
            name = "idx_seat_holds_showtime_seat",
            columnList = "showtime_id, seat_id"
        ),
        @Index(
            name = "idx_seat_holds_booking",
            columnList = "booking_id"
        ),
        @Index(
            name = "idx_seat_holds_expires_at",
            columnList = "expires_at"
        )
    }
)
public class SeatHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "showtime_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "FKseat_holds120529")
    )
    private Showtime showtime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "seat_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "FKseat_holds441818")
    )
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "booking_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "FKseat_holds543965")
    )
    private Booking booking;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}