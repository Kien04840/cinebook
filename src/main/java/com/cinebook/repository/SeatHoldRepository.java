package com.cinebook.repository;

import com.cinebook.entity.SeatHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SeatHoldRepository
        extends JpaRepository<SeatHold, Long> {

    List<SeatHold> findByShowtimeIdAndSeatId(
            String showtimeId,
            String seatId
    );

    Optional<SeatHold> findByShowtimeIdAndSeatIdAndBookingId(
            String showtimeId,
            String seatId,
            String bookingId
    );

    List<SeatHold> findByBookingId(String bookingId);

    List<SeatHold> findByShowtimeIdAndExpiresAtAfter(
            String showtimeId,
            LocalDateTime currentTime
    );

    @Query("""
        SELECT sh
        FROM SeatHold sh
        WHERE sh.showtime.id = :showtimeId
          AND sh.seat.id = :seatId
          AND sh.expiresAt > :currentTime
    """)
    Optional<SeatHold> findActiveHold(
            @Param("showtimeId") String showtimeId,
            @Param("seatId") String seatId,
            @Param("currentTime") LocalDateTime currentTime
    );

    @Modifying
    @Query("""
        DELETE FROM SeatHold sh
        WHERE sh.expiresAt <= :currentTime
    """)
    int deleteExpiredHolds(
            @Param("currentTime") LocalDateTime currentTime
    );
}