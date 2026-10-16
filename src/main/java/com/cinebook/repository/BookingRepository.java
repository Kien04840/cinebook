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

    Optional<Booking> findByCheckInCode(String checkInCode);

    boolean existsByCheckInCode(String checkInCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.checkInCode = :checkInCode")
    Optional<Booking> findByCheckInCodeWithLock(@Param("checkInCode") String checkInCode);

    @Query("""
        SELECT b FROM Booking b
        JOIN FETCH b.showtime s
        JOIN FETCH s.movie m
        JOIN FETCH s.auditorium a
        JOIN FETCH a.cinema c
        LEFT JOIN FETCH b.user u
        WHERE b.checkInCode = :checkInCode
    """)
    Optional<Booking> findByCheckInCodeWithDetails(@Param("checkInCode") String checkInCode);

    @Query("""
        SELECT b FROM Booking b
        JOIN FETCH b.showtime s
        JOIN FETCH s.movie m
        JOIN FETCH s.auditorium a
        JOIN FETCH a.cinema c
        JOIN FETCH b.user u
        WHERE b.bookingStatus = com.cinebook.enums.BookingStatus.PAID
          AND s.startTime BETWEEN :now AND :windowEnd
    """)
    List<Booking> findUpcomingPaidBookings(
            @Param("now") LocalDateTime now,
            @Param("windowEnd") LocalDateTime windowEnd,
            Pageable pageable
    );

    boolean existsByShowtimeId(String showtimeId);
 
    @Query("""
        SELECT COUNT(b) > 0
        FROM Booking b
        WHERE b.showtime.id = :showtimeId
          AND b.bookingStatus IN :statuses
    """)
    boolean existsByShowtimeIdAndBookingStatusIn(
            @Param("showtimeId") String showtimeId,
            @Param("statuses") java.util.Collection<BookingStatus> statuses
    );

    List<Booking> findByShowtimeIdAndBookingStatusIn(
            String showtimeId,
            java.util.Collection<BookingStatus> statuses
    );

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
        ORDER BY b.holdExpiresAt ASC
    """)
    Page<Booking> findExpiredBookings(
            @Param("status") BookingStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("""
        SELECT b FROM Booking b
        WHERE b.bookingStatus = :status
          AND b.holdExpiresAt <= :now
        ORDER BY b.holdExpiresAt ASC
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

    @Query("""
        SELECT COUNT(b) > 0
        FROM Booking b
        WHERE b.showtime.auditorium.id = :auditoriumId
    """)
    boolean existsByAuditoriumId(@Param("auditoriumId") String auditoriumId);

    @Query(value = """
        SELECT COUNT(*),
               COALESCE(SUM(CASE WHEN booking_status = 'PAID' THEN 1 ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN booking_status = 'CANCELLED' THEN 1 ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN booking_status = 'EXPIRED' THEN 1 ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN booking_status = 'REFUNDED' THEN 1 ELSE 0 END), 0)
        FROM bookings
        WHERE created_at >= :from AND created_at <= :to
    """, nativeQuery = true)
    Object[] countBookingKpiBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query(value = """
        SELECT COUNT(*),
               COALESCE(SUM(CASE WHEN booking_status = 'PAID' THEN 1 ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN booking_status = 'CANCELLED' THEN 1 ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN booking_status = 'EXPIRED' THEN 1 ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN booking_status = 'REFUNDED' THEN 1 ELSE 0 END), 0),
               COALESCE(SUM(total_amount), 0)
        FROM bookings
        WHERE created_at >= :from AND created_at <= :to
    """, nativeQuery = true)
    Object[] findBookingStatisticsBetweenNative(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    boolean existsByUserId(String userId);
}
