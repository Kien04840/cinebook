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
        String bookingId = (refund.getPayment() != null && refund.getPayment().getBooking() != null)
                ? refund.getPayment().getBooking().getId()
                : null;
        String bookingCode = (refund.getPayment() != null && refund.getPayment().getBooking() != null)
                ? refund.getPayment().getBooking().getBookingCode()
                : null;

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

