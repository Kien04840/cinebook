package com.cinebook.service;

import com.cinebook.dto.request.InitiatePaymentRequest;
import com.cinebook.dto.response.InitiatePaymentResponse;
import com.cinebook.dto.response.IpnResponse;
import com.cinebook.dto.response.PaymentResultResponse;
import com.cinebook.dto.response.PaymentSummaryResponse;
import jakarta.servlet.http.HttpServletRequest;

import com.cinebook.dto.request.RefundRequest;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.RefundResponse;
import com.cinebook.enums.RefundStatus;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface PaymentService {

    InitiatePaymentResponse initiatePayment(
            String bookingId,
            InitiatePaymentRequest request,
            HttpServletRequest httpRequest
    );

    IpnResponse processIpn(Map<String, String> params);

    PaymentResultResponse processReturn(Map<String, String> params);

    PaymentSummaryResponse getPaymentDetail(String paymentId);

    RefundResponse refundPayment(String paymentId, RefundRequest request, HttpServletRequest httpRequest);

    RefundResponse refundBooking(String bookingId, RefundRequest request, HttpServletRequest httpRequest);

    RefundResponse getRefundDetail(String paymentId);

    PageResponse<RefundResponse> getAdminRefunds(RefundStatus status, Pageable pageable);
}


