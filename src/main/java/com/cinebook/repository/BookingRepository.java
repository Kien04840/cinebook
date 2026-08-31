package com.cinebook.repository;

import com.cinebook.entity.Booking;
import com.cinebook.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdWithLock(@Param("id") String id);

    Optional<Booking> findByBookingCode(String bookingCode);


    boolean existsByBookingCode(String bookingCode);

    boolean existsByShowtimeId(String showtimeId);

    Optional<Booking> findByIdAndUserId(String id, String userId);

    Page<Booking> findByUserId(
            String userId,
            Pageable pageable
    );

    Page<Booking> findByUserIdAndBookingStatus(
            String userId,
            BookingStatus bookingStatus,
            Pageable pageable
    );

    Page<Booking> findByShowtimeId(
            String showtimeId,
            Pageable pageable
    );

    Page<Booking> findByBookingStatus(
            BookingStatus bookingStatus,
            Pageable pageable
    );

    @Query("""
        SELECT b FROM Booking b
        WHERE b.bookingStatus = :status
          AND b.holdExpiresAt <= :now
    """)
    List<Booking> findExpiredBookings(
            @Param("status") BookingStatus status,
            @Param("now") LocalDateTime now
    );

    long countByCreatedAtBetween(
            LocalDateTime from,
            LocalDateTime to
    );

    long countByBookingStatusAndCreatedAtBetween(
            BookingStatus bookingStatus,
            LocalDateTime from,
            LocalDateTime to
    );

    @Query("""
        SELECT COALESCE(SUM(b.totalAmount), 0)
        FROM Booking b
        WHERE b.createdAt >= :from AND b.createdAt <= :to
    """)
    java.math.BigDecimal findTotalBookingAmountBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}