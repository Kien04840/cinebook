package com.cinebook.mapper;

import com.cinebook.dto.response.RefundResponse;
import com.cinebook.entity.Refund;
import org.springframework.stereotype.Component;

@Component
public class RefundMapper {

    public RefundResponse toRefundResponse(Refund refund) {
        if (refund == null) {
            return null;
        }

        String paymentId = refund.getPayment() != null ? refund.getPayment().getId() : null;
        String bookingId = null;
        String bookingCode = null;
        if (refund.getPayment() != null && refund.getPayment().getBooking() != null) {
            try {
                bookingId = refund.getPayment().getBooking().getId();
                bookingCode = refund.getPayment().getBooking().getBookingCode();
            } catch (Exception ignored) {
                // Ignore if proxy is uninitialized outside transaction
            }
        }

        return RefundResponse.builder()
                .id(refund.getId())
                .paymentId(paymentId)
                .bookingId(bookingId)
                .bookingCode(bookingCode)
                .refundCode(refund.getRefundCode())
                .gatewayRefundId(refund.getGatewayRefundId())
                .amount(refund.getAmount())
                .refundReason(refund.getRefundReason())
                .refundStatus(refund.getRefundStatus())
                .processedAt(refund.getProcessedAt())
                .createdAt(refund.getCreatedAt())
                .build();
    }
}

