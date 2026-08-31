package com.cinebook.service.impl;

import com.cinebook.config.VnPayConfig;
import com.cinebook.entity.Booking;
import com.cinebook.entity.Payment;
import com.cinebook.service.VnPayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class VnPayServiceImpl implements VnPayService {

    private static final String HMAC_SHA512_ALGORITHM = "HmacSHA512";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final VnPayConfig vnPayConfig;

    @Override
    public String buildPaymentUrl(Payment payment, Booking booking, String clientIp) {
        Map<String, String> vnpParams = new HashMap<>();

        vnpParams.put("vnp_Version", vnPayConfig.getVersion());
        vnpParams.put("vnp_Command", vnPayConfig.getCommand());
        vnpParams.put("vnp_TmnCode", vnPayConfig.getTmnCode());

        long vnpAmount = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
        vnpParams.put("vnp_Amount", String.valueOf(vnpAmount));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", payment.getPaymentCode());
        vnpParams.put("vnp_OrderInfo", "Thanh toan ve xem phim " + booking.getBookingCode());
        vnpParams.put("vnp_OrderType", vnPayConfig.getOrderType());
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", vnPayConfig.getReturnUrl());
        vnpParams.put("vnp_IpAddr", StringUtils.hasText(clientIp) ? clientIp : "127.0.0.1");

        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        vnpParams.put("vnp_CreateDate", now.format(DATE_FORMATTER));

        LocalDateTime expireTime = booking.getHoldExpiresAt() != null
                ? booking.getHoldExpiresAt()
                : now.plusMinutes(5);
        vnpParams.put("vnp_ExpireDate", expireTime.format(DATE_FORMATTER));

        List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for (String fieldName : fieldNames) {
            String fieldValue = vnpParams.get(fieldName);
            if (StringUtils.hasText(fieldValue)) {
                try {
                    String encodedKey = URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString());
                    String encodedValue = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString());

                    // Build hash data
                    hashData.append(fieldName).append('=').append(encodedValue);

                    // Build query string
                    query.append(encodedKey).append('=').append(encodedValue);

                    query.append('&');
                    hashData.append('&');
                } catch (Exception e) {
                    log.error("Error encoding VNPay parameter {}: {}", fieldName, e.getMessage());
                }
            }
        }

        // Remove trailing '&'
        if (hashData.length() > 0) {
            hashData.setLength(hashData.length() - 1);
        }
        if (query.length() > 0) {
            query.setLength(query.length() - 1);
        }

        String secureHash = hmacSha512(vnPayConfig.getHashSecret(), hashData.toString());
        query.append("&vnp_SecureHash=").append(secureHash);

        return vnPayConfig.getPaymentUrl() + "?" + query;
    }

    @Override
    public boolean verifySignature(Map<String, String> params, String secureHash) {
        if (params == null || !StringUtils.hasText(secureHash) || !StringUtils.hasText(vnPayConfig.getHashSecret())) {
            return false;
        }

        String calculatedHash = calculateHmacSha512(params, vnPayConfig.getHashSecret());
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

        StringBuilder hashData = new StringBuilder();
        for (String fieldName : fieldNames) {
            String fieldValue = params.get(fieldName);
            if (StringUtils.hasText(fieldValue)
                    && !"vnp_SecureHash".equals(fieldName)
                    && !"vnp_SecureHashType".equals(fieldName)) {
                try {
                    String encodedValue = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString());
                    hashData.append(fieldName).append('=').append(encodedValue).append('&');
                } catch (Exception e) {
                    log.error("Error encoding VNPay field {}: {}", fieldName, e.getMessage());
                }
            }
        }

        if (hashData.length() > 0) {
            hashData.setLength(hashData.length() - 1);
        }

        return hmacSha512(secretKey, hashData.toString());
    }

    @Override
    public String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }

        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            // In case of multiple proxies, take first client IP
            int commaIndex = ip.indexOf(',');
            if (commaIndex > 0) {
                return ip.substring(0, commaIndex).trim();
            }
            return ip.trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }

        ip = request.getRemoteAddr();
        return StringUtils.hasText(ip) ? ip : "127.0.0.1";
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
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to calculate HMAC-SHA512: {}", e.getMessage());
            return "";
        }
    }
}

