package com.cinebook.repository;

import com.cinebook.entity.Payment;
import com.cinebook.enums.PaymentMethod;
import com.cinebook.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    Optional<Payment> findByPaymentCode(String paymentCode);

    boolean existsByPaymentCode(String paymentCode);

    Optional<Payment> findByGatewayTransactionId(
            String gatewayTransactionId
    );

    Optional<Payment> findByBookingId(String bookingId);

    Page<Payment> findByPaymentStatus(
            PaymentStatus paymentStatus,
            Pageable pageable
    );

    Page<Payment> findByPaymentMethod(
            PaymentMethod paymentMethod,
            Pageable pageable
    );
}