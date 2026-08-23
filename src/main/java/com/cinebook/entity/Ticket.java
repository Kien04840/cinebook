package com.cinebook.entity;

import com.cinebook.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
    name = "tickets",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_tickets_booking_seat",
            columnNames = {"booking_id", "seat_id"}
        )
    },
    indexes = {
        @Index(
            name = "idx_tickets_booking",
            columnList = "booking_id"
        ),
        @Index(
            name = "idx_tickets_seat",
            columnList = "seat_id"
        )
    }
)
public class Ticket {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(
        name = "ticket_price",
        precision = 12,
        scale = 2,
        nullable = false
    )
    private BigDecimal ticketPrice;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "ticket_status",
        length = 20,
        nullable = false
    )
    private TicketStatus ticketStatus;

    @Column(
        name = "qr_code",
        length = 255,
        unique = true
    )
    private String qrCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "booking_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "FKtickets264119")
    )
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "seat_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "FKtickets721687")
    )
    private Seat seat;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}