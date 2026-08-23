package com.cinebook.entity;

import com.cinebook.enums.BookingStatus;
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
    name = "bookings",
    indexes = {
        @Index(
            name = "idx_bookings_user_created",
            columnList = "user_id, created_at"
        ),
        @Index(
            name = "idx_bookings_showtime_status",
            columnList = "showtime_id, booking_status"
        )
    }
)
public class Booking {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(
        name = "booking_code",
        length = 30,
        nullable = false,
        unique = true,
        updatable = false
    )
    private String bookingCode;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(
    name = "user_id",
    nullable = false,
    foreignKey = @ForeignKey(name = "FKbookings722397")
)
private User user;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(
    name = "showtime_id",
    nullable = false,
    foreignKey = @ForeignKey(name = "FKbookings564687")
)
private Showtime showtime;

@Column(
    name = "total_amount",
    precision = 12,
    scale = 2,
    nullable = false
)
private BigDecimal totalAmount;

@Enumerated(EnumType.STRING)
@Column(
    name = "booking_status",
    length = 30,
    nullable = false
)
private BookingStatus bookingStatus;

@Column(name = "hold_expires_at")
private LocalDateTime holdExpiresAt;

@Column(name = "cancelled_at")
private LocalDateTime cancelledAt;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(
    name = "cancelled_by_user_id",
    foreignKey = @ForeignKey(name = "FKbookings_cancelled_by_user")
)
private User cancelledByUser;

@Column(name = "cancelled_reason", length = 500)
private String cancelledReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(
        mappedBy = "booking",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<Ticket> tickets = new ArrayList<>();

    @OneToMany(
        mappedBy = "booking",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<SeatHold> seatHolds = new ArrayList<>();

    @OneToMany(mappedBy = "booking")
    private List<Payment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "booking")
    private List<BookingPromotion> bookingPromotions = new ArrayList<>();

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