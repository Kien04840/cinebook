package com.cinebook.service;

import com.cinebook.entity.Booking;
import com.cinebook.entity.Payment;
import com.cinebook.entity.Refund;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

public interface VnPayService {

    String buildPaymentUrl(Payment payment, Booking booking, String clientIp);

    boolean verifySignature(Map<String, String> params, String secureHash);

    String calculateHmacSha512(Map<String, String> params, String secretKey);

    String extractClientIp(HttpServletRequest request);

    Map<String, String> refundPayment(Payment payment, Refund refund, String userEmail, String clientIp);
}


