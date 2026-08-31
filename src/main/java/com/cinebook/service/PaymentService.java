package com.cinebook.service;

import com.cinebook.dto.request.InitiatePaymentRequest;
import com.cinebook.dto.response.InitiatePaymentResponse;
import com.cinebook.dto.response.IpnResponse;
import com.cinebook.dto.response.PaymentResultResponse;
import com.cinebook.dto.response.PaymentSummaryResponse;
import jakarta.servlet.http.HttpServletRequest;

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
}

