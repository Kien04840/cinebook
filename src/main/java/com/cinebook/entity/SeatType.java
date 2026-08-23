package com.cinebook.entity;

import com.cinebook.enums.SeatTypeStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
    name = "seat_types",
    indexes = {
        @Index(name = "idx_seat_types_status", columnList = "status")
    }
)
public class SeatType {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(length = 100, nullable = false, unique = true)
    private String name;

    @Column(name = "price_modifier", precision = 10, scale = 2, nullable = false)
    private BigDecimal priceModifier;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private SeatTypeStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "seatType")
    private Set<Seat> seats = new HashSet<>();

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