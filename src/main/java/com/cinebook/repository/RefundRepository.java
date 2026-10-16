package com.cinebook.repository;

import com.cinebook.entity.Refund;
import com.cinebook.enums.RefundStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, String> {

    Optional<Refund> findByRefundCode(String refundCode);

    boolean existsByRefundCode(String refundCode);

    Optional<Refund> findByGatewayRefundId(
            String gatewayRefundId
    );

    Optional<Refund> findByPaymentId(String paymentId);

    boolean existsByPaymentId(String paymentId);

    boolean existsByPaymentIdAndRefundStatus(String paymentId, RefundStatus refundStatus);

    @Query("SELECT r FROM Refund r WHERE (:status IS NULL OR r.refundStatus = :status) ORDER BY r.createdAt DESC")
    Page<Refund> findAdminRefunds(@Param("status") RefundStatus status, Pageable pageable);

    Page<Refund> findByRefundStatus(
            RefundStatus refundStatus,
            Pageable pageable
    );

    @Query("""
        SELECT COALESCE(SUM(r.amount), 0)
        FROM Refund r
        WHERE r.refundStatus = com.cinebook.enums.RefundStatus.SUCCESS
          AND r.processedAt >= :from AND r.processedAt <= :to
    """)
    java.math.BigDecimal findTotalRefundAmountBetween(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to
    );

    long countByProcessedAtBetween(
            java.time.LocalDateTime from,
            java.time.LocalDateTime to
    );

    long countByRefundStatusAndProcessedAtBetween(
            RefundStatus refundStatus,
            java.time.LocalDateTime from,
            java.time.LocalDateTime to
    );

    @Query("""
        SELECT r
        FROM Refund r
        JOIN FETCH r.payment p
        JOIN FETCH p.booking b
        JOIN FETCH b.showtime s
        JOIN FETCH s.movie m
        JOIN FETCH s.auditorium a
        JOIN FETCH a.cinema c
        WHERE r.refundStatus = com.cinebook.enums.RefundStatus.SUCCESS
          AND r.processedAt >= :from AND r.processedAt <= :to
        ORDER BY r.processedAt ASC
    """)
    java.util.List<Refund> findSuccessfulRefundsBetween(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to
    );

    @Query(value = """
        SELECT DATE_FORMAT(r.processed_at, :dateFormat) AS period,
               COALESCE(SUM(r.amount), 0) AS refund_amount
        FROM refunds r
        WHERE r.refund_status = 'SUCCESS'
          AND r.processed_at >= :from AND r.processed_at <= :to
        GROUP BY DATE_FORMAT(r.processed_at, :dateFormat)
        ORDER BY period ASC
    """, nativeQuery = true)
    java.util.List<Object[]> findRefundTrendNative(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to,
            @Param("dateFormat") String dateFormat
    );

    @Query(value = """
        SELECT m.id AS movie_id,
               COALESCE(SUM(r.amount), 0) AS refund_amount
        FROM refunds r
        JOIN payments p ON r.payment_id = p.id
        JOIN bookings b ON p.booking_id = b.id
        JOIN showtimes s ON b.showtime_id = s.id
        JOIN movies m ON s.movie_id = m.id
        WHERE r.refund_status = 'SUCCESS'
          AND r.processed_at >= :from AND r.processed_at <= :to
        GROUP BY m.id
    """, nativeQuery = true)
    java.util.List<Object[]> findMovieRefundReportNative(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to
    );

    @Query(value = """
        SELECT c.id AS cinema_id,
               COALESCE(SUM(r.amount), 0) AS refund_amount
        FROM refunds r
        JOIN payments p ON r.payment_id = p.id
        JOIN bookings b ON p.booking_id = b.id
        JOIN showtimes s ON b.showtime_id = s.id
        JOIN auditoriums a ON s.auditorium_id = a.id
        JOIN cinemas c ON a.cinema_id = c.id
        WHERE r.refund_status = 'SUCCESS'
          AND r.processed_at >= :from AND r.processed_at <= :to
        GROUP BY c.id
    """, nativeQuery = true)
    java.util.List<Object[]> findCinemaRefundReportNative(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to
    );

    @Query(value = """
        SELECT COUNT(*),
               COALESCE(SUM(CASE WHEN refund_status = 'SUCCESS' THEN 1 ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN refund_status = 'FAILED' THEN 1 ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN refund_status = 'PENDING' THEN 1 ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN refund_status = 'SUCCESS' THEN amount ELSE 0 END), 0)
        FROM refunds
        WHERE processed_at >= :from AND processed_at <= :to
    """, nativeQuery = true)
    Object[] findRefundStatisticsNative(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to
    );
}
