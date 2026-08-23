package com.cinebook.entity;

import com.cinebook.enums.SeatStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
    name = "seats",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_seats_auditorium_position",
            columnNames = {
                "auditorium_id",
                "row_label",
                "seat_number"
            }
        )
    },
    indexes = {
        @Index(name = "idx_seats_seat_type_id", columnList = "seat_type_id")
    }
)
public class Seat {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "auditorium_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "FKseats380745")
    )
    private Auditorium auditorium;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "seat_type_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "FKseats419573")
    )
    private SeatType seatType;

    @Column(name = "row_label", length = 1, nullable = false)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private Short seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private SeatStatus status;

    @OneToMany(mappedBy = "seat")
    private List<SeatHold> seatHolds = new ArrayList<>();

    @OneToMany(mappedBy = "seat")
    private List<Ticket> tickets = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}