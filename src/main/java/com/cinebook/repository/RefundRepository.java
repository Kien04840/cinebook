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
}
