package com.cinebook.entity;

import com.cinebook.enums.RefundStatus;
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
    name = "refunds",
    indexes = {
        @Index(
            name = "idx_refunds_gateway_id",
            columnList = "gateway_refund_id"
        )
    }
)
public class Refund {

    @Id
    @Column(
        length = 36,
        nullable = false,
        updatable = false
    )
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "payment_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_refunds_payment")
    )
    private Payment payment;

    @Column(
        name = "refund_code",
        length = 50,
        nullable = false,
        unique = true,
        updatable = false
    )
    private String refundCode;

    @Column(
        name = "gateway_refund_id",
        length = 100
    )
    private String gatewayRefundId;

    @Column(
        precision = 12,
        scale = 2,
        nullable = false
    )
    private BigDecimal amount;

    @Column(
        name = "refund_reason",
        length = 255
    )
    private String refundReason;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "refund_status",
        length = 20,
        nullable = false
    )
    private RefundStatus refundStatus;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
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