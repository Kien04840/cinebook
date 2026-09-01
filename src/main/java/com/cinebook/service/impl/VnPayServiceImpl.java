package com.cinebook.service.impl;

import com.cinebook.config.VnPayConfig;
import com.cinebook.entity.Booking;
import com.cinebook.entity.Payment;
import com.cinebook.entity.Refund;
import com.cinebook.exception.BadRequestException;
import com.cinebook.service.VnPayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "cinebook.payment.gateway", havingValue = "vnpay")
public class VnPayServiceImpl implements VnPayService {

    private static final String HMAC_SHA512_ALGORITHM = "HmacSHA512";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final VnPayConfig vnPayConfig;

    @PostConstruct
    public void logGatewayStatus() {
        boolean tmnConfigured = isConfigured(vnPayConfig.getTmnCode());
        boolean secretConfigured = isConfigured(vnPayConfig.getHashSecret());
        log.info("=== VNPay Gateway Configuration ===");
        log.info("VNPay TMN Code configured: {}", tmnConfigured);
        log.info("VNPay Hash Secret configured: {}", secretConfigured);
        log.info("VNPay Payment URL: {}", vnPayConfig.getPaymentUrl());
        log.info("VNPay Return URL: {}", vnPayConfig.getReturnUrl());
    }

    private boolean isConfigured(String val) {
        if (!StringUtils.hasText(val)) {
            return false;
        }
        String trimmed = val.trim();
        return !trimmed.startsWith("YOUR_") && !trimmed.contains("CHANGE_ME");
    }

    @Override
    public String buildPaymentUrl(Payment payment, Booking booking, String clientIp) {
        if (!isConfigured(vnPayConfig.getTmnCode()) || !isConfigured(vnPayConfig.getHashSecret())) {
            log.error("VNPay credentials are not configured properly (missing or placeholder).");
            throw new BadRequestException("Cổng thanh toán VNPay chưa được cấu hình credentials hợp lệ.");
        }

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
                    String encodedKey = URLEncoder.encode(fieldName, StandardCharsets.UTF_8.toString());
                    String encodedValue = URLEncoder.encode(fieldValue, StandardCharsets.UTF_8.toString());

                    // Build hash data
                    hashData.append(encodedKey).append('=').append(encodedValue).append('&');

                    // Build query string
                    query.append(encodedKey).append('=').append(encodedValue).append('&');
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
                    String encodedKey = URLEncoder.encode(fieldName, StandardCharsets.UTF_8.toString());
                    String encodedValue = URLEncoder.encode(fieldValue, StandardCharsets.UTF_8.toString());
                    hashData.append(encodedKey).append('=').append(encodedValue).append('&');
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

    public Map<String, String> refundPayment(Payment payment, Refund refund, String userEmail, String clientIp) {
        Map<String, String> result = new HashMap<>();
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        String version = vnPayConfig.getVersion();
        String command = "refund";
        String tmnCode = vnPayConfig.getTmnCode();
        String transactionType = "02"; // Full refund
        String txnRef = payment.getPaymentCode();
        long amount = refund.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
        String orderInfo = "Hoan tien giao dich " + txnRef;
        String transactionNo = (StringUtils.hasText(payment.getGatewayTransactionId()) && payment.getGatewayTransactionId().matches("\\d+"))
                ? payment.getGatewayTransactionId()
                : "0";


        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        String createDate = now.format(DATE_FORMATTER);
        String transactionDate = (payment.getPaidAt() != null)
                ? payment.getPaidAt().format(DATE_FORMATTER)
                : createDate;
        String createBy = StringUtils.hasText(userEmail) ? userEmail : "System";
        String ipAddr = StringUtils.hasText(clientIp) ? clientIp : "127.0.0.1";

        // Raw data format: vnp_RequestId|vnp_Version|vnp_Command|vnp_TmnCode|vnp_TransactionType|vnp_TxnRef|vnp_Amount|vnp_TransactionNo|vnp_TransactionDate|vnp_CreateBy|vnp_CreateDate|vnp_IpAddr|vnp_OrderInfo
        String rawHashData = String.join("|",
                requestId,
                version,
                command,
                tmnCode,
                transactionType,
                txnRef,
                String.valueOf(amount),
                transactionNo,
                transactionDate,
                createBy,
                createDate,
                ipAddr,
                orderInfo
        );

        String secureHash = hmacSha512(vnPayConfig.getHashSecret(), rawHashData);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("vnp_RequestId", requestId);
        requestBody.put("vnp_Version", version);
        requestBody.put("vnp_Command", command);
        requestBody.put("vnp_TmnCode", tmnCode);
        requestBody.put("vnp_TransactionType", transactionType);
        requestBody.put("vnp_TxnRef", txnRef);
        requestBody.put("vnp_Amount", amount);
        requestBody.put("vnp_OrderInfo", orderInfo);
        requestBody.put("vnp_TransactionNo", transactionNo);
        requestBody.put("vnp_TransactionDate", transactionDate);
        requestBody.put("vnp_CreateBy", createBy);
        requestBody.put("vnp_CreateDate", createDate);
        requestBody.put("vnp_IpAddr", ipAddr);
        requestBody.put("vnp_SecureHash", secureHash);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            HttpClient client = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();


            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(vnPayConfig.getApiUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("VNPay Refund API response status {}: {}", response.statusCode(), response.body());

            if (response.statusCode() == 200 && StringUtils.hasText(response.body())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> respMap = objectMapper.readValue(response.body(), Map.class);
                String responseCode = respMap.get("vnp_ResponseCode") != null ? String.valueOf(respMap.get("vnp_ResponseCode")) : "";
                String responseId = respMap.get("vnp_ResponseId") != null ? String.valueOf(respMap.get("vnp_ResponseId")) : "";
                String message = respMap.get("vnp_Message") != null ? String.valueOf(respMap.get("vnp_Message")) : "";

                result.put("vnp_ResponseCode", responseCode);
                result.put("vnp_ResponseId", responseId);
                result.put("vnp_Message", message);
                result.put("rawResponse", response.body());
                return result;
            } else {
                result.put("vnp_ResponseCode", "99");
                result.put("vnp_Message", "HTTP error " + response.statusCode());
                result.put("rawResponse", response.body());
                return result;
            }
        } catch (Exception e) {
            log.error("Exception while sending VNPay refund request: {}", e.getMessage());
            result.put("vnp_ResponseCode", "99");
            result.put("vnp_Message", "Exception: " + e.getMessage());
            return result;
        }
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


