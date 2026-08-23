package com.cinebook.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
    name = "booking_promotions",
    indexes = {
        @Index(
            name = "idx_booking_promotions_booking",
            columnList = "booking_id"
        )
    }
)
public class BookingPromotion {

    @EmbeddedId
    private BookingPromotionId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("promotionId")
    @JoinColumn(
        name = "promotion_id",
        nullable = false,
        foreignKey = @ForeignKey(
            name = "fk_booking_promotions_promotion"
        )
    )
    private Promotion promotion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("bookingId")
    @JoinColumn(
        name = "booking_id",
        nullable = false,
        foreignKey = @ForeignKey(
            name = "fk_booking_promotions_booking"
        )
    )
    private Booking booking;

    @Column(
        name = "discount_amount",
        precision = 12,
        scale = 2,
        nullable = false
    )
    private BigDecimal discountAmount;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}