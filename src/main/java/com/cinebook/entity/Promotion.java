package com.cinebook.entity;

import com.cinebook.enums.PromotionDiscountType;
import com.cinebook.enums.PromotionStatus;
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
    name = "promotions",
    indexes = {
        @Index(
            name = "idx_promotions_status_period",
            columnList = "status, start_at, end_at"
        )
    }
)
public class Promotion {

    @Id
    @Column(
        length = 36,
        nullable = false,
        updatable = false
    )
    private String id;

    @Column(
        length = 50,
        nullable = false,
        unique = true,
        updatable = false
    )
    private String code;

    @Column(
        length = 255,
        nullable = false
    )
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "discount_type",
        length = 20,
        nullable = false
    )
    private PromotionDiscountType discountType;

    @Column(
        name = "discount_value",
        precision = 12,
        scale = 2,
        nullable = false
    )
    private BigDecimal discountValue;

    @Column(
        name = "min_order_amount",
        precision = 12,
        scale = 2
    )
    private BigDecimal minOrderAmount;

    @Column(
        name = "max_discount_amount",
        precision = 12,
        scale = 2
    )
    private BigDecimal maxDiscountAmount;

    @Column(
        name = "start_at",
        nullable = false
    )
    private LocalDateTime startAt;

    @Column(
        name = "end_at",
        nullable = false
    )
    private LocalDateTime endAt;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private Integer usedCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(
        length = 20,
        nullable = false
    )
    private PromotionStatus status;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
        name = "updated_at",
        nullable = false
    )
    private LocalDateTime updatedAt;

    @Version
    @Column(
        nullable = false
    )
    private Long version;

    @OneToMany(mappedBy = "promotion")
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

        if (usedCount == null) {
            usedCount = 0;
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