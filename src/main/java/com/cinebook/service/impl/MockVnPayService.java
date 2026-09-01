package com.cinebook.service.impl;

import com.cinebook.config.VnPayConfig;
import com.cinebook.entity.Booking;
import com.cinebook.entity.Payment;
import com.cinebook.entity.Refund;
import com.cinebook.service.VnPayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "cinebook.payment.gateway", havingValue = "mock", matchIfMissing = true)
public class MockVnPayService implements VnPayService {

    private static final String HMAC_SHA512_ALGORITHM = "HmacSHA512";

    private final VnPayConfig vnPayConfig;

    @Value("${cinebook.payment.mock.refund-result:SUCCESS}")
    private String mockRefundResult;

    @Override
    public String buildPaymentUrl(Payment payment, Booking booking, String clientIp) {
        log.info("[MOCK GATEWAY] Generating mock payment URL for payment {} (booking={})",
                payment.getPaymentCode(), booking.getBookingCode());

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", vnPayConfig.getVersion());
        vnpParams.put("vnp_Command", vnPayConfig.getCommand());
        vnpParams.put("vnp_TmnCode", StringUtils.hasText(vnPayConfig.getTmnCode()) ? vnPayConfig.getTmnCode() : "MOCK_TMN");

        long vnpAmount = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
        vnpParams.put("vnp_Amount", String.valueOf(vnpAmount));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", payment.getPaymentCode());
        vnpParams.put("vnp_OrderInfo", "Thanh toan ve xem phim " + booking.getBookingCode());
        vnpParams.put("vnp_OrderType", vnPayConfig.getOrderType());
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", vnPayConfig.getReturnUrl());
        vnpParams.put("vnp_IpAddr", sanitizeClientIp(clientIp));

        List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        for (String fieldName : fieldNames) {
            String fieldValue = vnpParams.get(fieldName);
            if (StringUtils.hasText(fieldValue)) {
                try {
                    String encodedKey = URLEncoder.encode(fieldName, StandardCharsets.UTF_8.toString());
                    String encodedValue = URLEncoder.encode(fieldValue, StandardCharsets.UTF_8.toString());

                    hashData.append(encodedKey).append('=').append(encodedValue).append('&');
                    query.append(encodedKey).append('=').append(encodedValue).append('&');
                } catch (Exception e) {
                    log.error("[MOCK GATEWAY] Error encoding field {}: {}", fieldName, e.getMessage());
                }
            }
        }

        if (hashData.length() > 0) {
            hashData.setLength(hashData.length() - 1);
        }
        if (query.length() > 0) {
            query.setLength(query.length() - 1);
        }

        String secretKey = StringUtils.hasText(vnPayConfig.getHashSecret()) ? vnPayConfig.getHashSecret() : "MOCK_SECRET_KEY";
        String secureHash = hmacSha512(secretKey, hashData.toString());
        query.append("&vnp_SecureHash=").append(secureHash);

        return vnPayConfig.getPaymentUrl() + "?" + query;
    }

    @Override
    public boolean verifySignature(Map<String, String> params, String secureHash) {
        if (params == null || !StringUtils.hasText(secureHash)) {
            return false;
        }

        String secretKey = StringUtils.hasText(vnPayConfig.getHashSecret()) ? vnPayConfig.getHashSecret() : "MOCK_SECRET_KEY";
        String calculatedHash = calculateHmacSha512(params, secretKey);
        if (!StringUtils.hasText(calculatedHash)) {
            return false;
        }

        byte[] calculatedBytes = calculatedHash.toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = secureHash.toLowerCase().getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(calculatedBytes, expectedBytes);
    }

    @Override
    public String calculateHmacSha512(Map<String, String> params, String secretKey) {
        if (params == null || !StringUtils.hasText(secretKey)) {
            return "";
        }

        List<String> fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames);

        StringBuilder sb = new StringBuilder();
        for (String fieldName : fieldNames) {
            String fieldValue = params.get(fieldName);
            if (StringUtils.hasText(fieldValue) && !"vnp_SecureHash".equals(fieldName) && !"vnp_SecureHashType".equals(fieldName)) {
                try {
                    String encodedKey = URLEncoder.encode(fieldName, StandardCharsets.UTF_8.toString());
                    String encodedValue = URLEncoder.encode(fieldValue, StandardCharsets.UTF_8.toString());
                    sb.append(encodedKey).append('=').append(encodedValue).append('&');
                } catch (Exception e) {
                    log.error("[MOCK GATEWAY] Error encoding field {}: {}", fieldName, e.getMessage());
                }
            }
        }

        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }

        return hmacSha512(secretKey, sb.toString());
    }

    @Override
    public String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            int commaIndex = ip.indexOf(',');
            if (commaIndex > 0) {
                return sanitizeClientIp(ip.substring(0, commaIndex));
            }
            return sanitizeClientIp(ip);
        }
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return sanitizeClientIp(ip);
        }
        return sanitizeClientIp(request.getRemoteAddr());
    }

    private String sanitizeClientIp(String ip) {
        if (!StringUtils.hasText(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip) || "localhost".equalsIgnoreCase(ip) || ip.contains(":")) {
            return "127.0.0.1";
        }
        return ip.trim();
    }

    @Override
    public Map<String, String> refundPayment(Payment payment, Refund refund, String userEmail, String clientIp) {
        Map<String, String> result = new HashMap<>();
        String gatewayRefundId = "MOCK-REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        if ("FAILURE".equalsIgnoreCase(mockRefundResult)) {
            log.warn("[MOCK GATEWAY] Simulating REFUND FAILURE for payment {} (refundCode={})",
                    payment.getPaymentCode(), refund.getRefundCode());
            result.put("vnp_ResponseCode", "99");
            result.put("vnp_ResponseId", gatewayRefundId);
            result.put("vnp_Message", "Simulated mock refund failure");
            result.put("rawResponse", "{\"vnp_ResponseCode\":\"99\",\"vnp_Message\":\"Simulated mock refund failure\"}");
            return result;
        }

        log.info("[MOCK GATEWAY] Simulating REFUND SUCCESS for payment {} (refundCode={}) -> gatewayRefundId={}",
                payment.getPaymentCode(), refund.getRefundCode(), gatewayRefundId);

        result.put("vnp_ResponseCode", "00");
        result.put("vnp_ResponseId", gatewayRefundId);
        result.put("vnp_Message", "Mock refund success");
        result.put("rawResponse", "{\"vnp_ResponseCode\":\"00\",\"vnp_ResponseId\":\"" + gatewayRefundId + "\",\"vnp_Message\":\"Mock refund success\"}");

        return result;
    }

    private String hmacSha512(String key, String data) {
        try {
            if (!StringUtils.hasText(key) || data == null) {
                return "";
            }
            Mac hmac512 = Mac.getInstance(HMAC_SHA512_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA512_ALGORITHM);
            hmac512.init(secretKey);
            byte[] result = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("[MOCK GATEWAY] Error calculating HMAC-SHA512: {}", e.getMessage());
            return "";
        }
    }
}

