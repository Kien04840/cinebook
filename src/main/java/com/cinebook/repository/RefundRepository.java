package com.cinebook.repository;

import com.cinebook.entity.Refund;
import com.cinebook.enums.RefundStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, String> {

    Optional<Refund> findByRefundCode(String refundCode);

    boolean existsByRefundCode(String refundCode);

    Optional<Refund> findByGatewayRefundId(
            String gatewayRefundId
    );

    Optional<Refund> findByPaymentId(String paymentId);

    Page<Refund> findByRefundStatus(
            RefundStatus refundStatus,
            Pageable pageable
    );
}