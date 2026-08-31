package com.cinebook.repository;

import com.cinebook.entity.Payment;
import com.cinebook.enums.PaymentMethod;
import com.cinebook.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdWithLock(@Param("id") String id);

    Optional<Payment> findByPaymentCode(String paymentCode);

    boolean existsByPaymentCode(String paymentCode);

    Optional<Payment> findByGatewayTransactionId(
            String gatewayTransactionId
    );

    List<Payment> findByBookingId(String bookingId);
    List<Payment> findByBookingIdOrderByCreatedAtDesc(String bookingId);

    Optional<Payment> findFirstByBookingIdAndPaymentStatus(String bookingId, PaymentStatus paymentStatus);
    Optional<Payment> findFirstByBookingIdOrderByCreatedAtDesc(String bookingId);

    boolean existsByBookingIdAndPaymentStatus(String bookingId, PaymentStatus paymentStatus);

    Page<Payment> findByPaymentStatus(
            PaymentStatus paymentStatus,
            Pageable pageable
    );

    Page<Payment> findByPaymentMethod(
            PaymentMethod paymentMethod,
            Pageable pageable
    );

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM Payment p
        WHERE p.paymentStatus IN (com.cinebook.enums.PaymentStatus.SUCCESS, com.cinebook.enums.PaymentStatus.REFUNDED)
          AND p.paidAt >= :from AND p.paidAt <= :to
    """)
    java.math.BigDecimal findGrossRevenueBetween(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to
    );

    @Query("""
        SELECT p
        FROM Payment p
        JOIN FETCH p.booking b
        JOIN FETCH b.showtime s
        JOIN FETCH s.movie m
        JOIN FETCH s.auditorium a
        JOIN FETCH a.cinema c
        WHERE p.paymentStatus IN (com.cinebook.enums.PaymentStatus.SUCCESS, com.cinebook.enums.PaymentStatus.REFUNDED)
          AND p.paidAt >= :from AND p.paidAt <= :to
        ORDER BY p.paidAt ASC
    """)
    List<Payment> findSuccessfulPaymentsBetween(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to
    );
}
