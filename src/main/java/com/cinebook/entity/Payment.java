package com.cinebook.entity;

import com.cinebook.enums.PaymentMethod;
import com.cinebook.enums.PaymentStatus;
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
    name = "payments",
    indexes = {
        @Index(
            name = "idx_payments_booking",
            columnList = "booking_id"
        ),
        @Index(
            name = "idx_payments_gateway_transaction",
            columnList = "gateway_transaction_id"
        )
    }
)
public class Payment {

    @Id
    @Column(
        length = 36,
        nullable = false,
        updatable = false
    )
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "booking_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_payments_booking")
    )
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "payment_method",
        length = 20,
        nullable = false
    )
    private PaymentMethod paymentMethod;

    @Column(
        name = "payment_code",
        length = 50,
        nullable = false,
        unique = true,
        updatable = false
    )
    private String paymentCode;

    @Column(
        name = "gateway_transaction_id",
        length = 100
    )
    private String gatewayTransactionId;

    @Column(
        precision = 12,
        scale = 2,
        nullable = false
    )
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "payment_status",
        length = 20,
        nullable = false
    )
    private PaymentStatus paymentStatus;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(
        name = "gateway_response",
        columnDefinition = "json"
    )
    private String gatewayResponse;

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

    @OneToOne(
        mappedBy = "payment",
        fetch = FetchType.LAZY
    )
    private Refund refund;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}