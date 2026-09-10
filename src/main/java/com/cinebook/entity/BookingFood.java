package com.cinebook.entity;

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
    name = "booking_foods",
    indexes = {
        @Index(name = "idx_booking_foods_booking", columnList = "booking_id"),
        @Index(name = "idx_booking_foods_food_item", columnList = "food_item_id")
    }
)
public class BookingFood {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "booking_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_booking_foods_booking")
    )
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "food_item_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_booking_foods_food_item")
    )
    private FoodItem foodItem;

    @Column(name = "food_name", length = 100, nullable = false)
    private String foodName;

    @Column(name = "unit_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal subtotal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

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

