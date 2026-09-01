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

    java.util.List<Booking> findByUserId(String userId);

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

    @Query(value = """
        SELECT b FROM Booking b
        JOIN FETCH b.user u
        JOIN FETCH b.showtime s
        JOIN FETCH s.movie m
        JOIN FETCH s.auditorium a
        JOIN FETCH a.cinema c
        WHERE (:status IS NULL OR b.bookingStatus = :status)
          AND (:showtimeId IS NULL OR b.showtime.id = :showtimeId)
          AND (:keyword IS NULL OR LOWER(b.bookingCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(u.phone) LIKE LOWER(CONCAT('%', :keyword, '%')))
    """, countQuery = """
        SELECT count(b) FROM Booking b
        WHERE (:status IS NULL OR b.bookingStatus = :status)
          AND (:showtimeId IS NULL OR b.showtime.id = :showtimeId)
          AND (:keyword IS NULL OR LOWER(b.bookingCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(b.user.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(b.user.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(b.user.phone) LIKE LOWER(CONCAT('%', :keyword, '%')))
    """)
    Page<Booking> findAdminBookings(
            @Param("keyword") String keyword,
            @Param("status") BookingStatus status,
            @Param("showtimeId") String showtimeId,
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

    @Query("""
        SELECT b FROM Booking b
        WHERE b.user.id = :userId
          AND b.showtime.id = :showtimeId
          AND b.bookingStatus = 'PENDING_PAYMENT'
          AND b.holdExpiresAt > :now
        ORDER BY b.createdAt DESC
    """)
    List<Booking> findActiveBookingsByUserAndShowtime(
            @Param("userId") String userId,
            @Param("showtimeId") String showtimeId,
            @Param("now") LocalDateTime now
    );
}