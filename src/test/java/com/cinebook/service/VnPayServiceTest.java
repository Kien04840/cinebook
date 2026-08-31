package com.cinebook.service;

import com.cinebook.config.VnPayConfig;
import com.cinebook.entity.Booking;
import com.cinebook.entity.Payment;
import com.cinebook.entity.Showtime;
import com.cinebook.enums.BookingStatus;
import com.cinebook.enums.PaymentMethod;
import com.cinebook.enums.PaymentStatus;
import com.cinebook.service.impl.VnPayServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VnPayServiceTest {

    private VnPayConfig vnPayConfig;
    private VnPayServiceImpl vnPayService;

    private static final String TEST_TMN_CODE = "2QXUI4J4";
    private static final String TEST_HASH_SECRET = "RAXMQCKQZUNNUIKQQFJSUBJWBYQXZTXU";

    @BeforeEach
    void setUp() {
        vnPayConfig = new VnPayConfig();
        vnPayConfig.setTmnCode(TEST_TMN_CODE);
        vnPayConfig.setHashSecret(TEST_HASH_SECRET);
        vnPayConfig.setPaymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        vnPayConfig.setReturnUrl("http://localhost:5173/payment/result");
        vnPayConfig.setVersion("2.1.0");
        vnPayConfig.setCommand("pay");
        vnPayConfig.setOrderType("other");

        vnPayService = new VnPayServiceImpl(vnPayConfig);
    }

    @Test
    @DisplayName("HMAC-SHA512 Calculation - Deterministic hash on known test vectors")
    void testCalculateHmacSha512() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "24000000");
        params.put("vnp_Command", "pay");
        params.put("vnp_CreateDate", "20260901100000");
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_IpAddr", "127.0.0.1");
        params.put("vnp_Locale", "vn");
        params.put("vnp_OrderInfo", "Thanh toan ve xem phim CB-20260901-001");
        params.put("vnp_OrderType", "other");
        params.put("vnp_ReturnUrl", "http://localhost:5173/payment/result");
        params.put("vnp_TmnCode", TEST_TMN_CODE);
        params.put("vnp_TxnRef", "PAY-20260901-ABC12345");
        params.put("vnp_Version", "2.1.0");

        String hash1 = vnPayService.calculateHmacSha512(params, TEST_HASH_SECRET);
        String hash2 = vnPayService.calculateHmacSha512(params, TEST_HASH_SECRET);

        assertThat(hash1).isNotEmpty();
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(128); // 512 bits in hex = 128 characters
    }

    @Test
    @DisplayName("verifySignature - Valid signature returns true")
    void testVerifySignature_Valid() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "10000000");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan ve xem phim");
        params.put("vnp_PayDate", "20260901100500");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", TEST_TMN_CODE);
        params.put("vnp_TransactionNo", "14567890");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", "PAY-20260901-ABC12345");

        String secureHash = vnPayService.calculateHmacSha512(params, TEST_HASH_SECRET);
        params.put("vnp_SecureHash", secureHash);

        boolean isValid = vnPayService.verifySignature(params, secureHash);
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("verifySignature - Tampered amount returns false")
    void testVerifySignature_TamperedAmount_ReturnsFalse() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "10000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", TEST_TMN_CODE);
        params.put("vnp_TxnRef", "PAY-20260901-ABC12345");

        String secureHash = vnPayService.calculateHmacSha512(params, TEST_HASH_SECRET);

        // Tamper amount
        params.put("vnp_Amount", "5000000");

        boolean isValid = vnPayService.verifySignature(params, secureHash);
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("verifySignature - Tampered responseCode returns false")
    void testVerifySignature_TamperedResponseCode_ReturnsFalse() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "10000000");
        params.put("vnp_ResponseCode", "24"); // Cancelled
        params.put("vnp_TmnCode", TEST_TMN_CODE);
        params.put("vnp_TxnRef", "PAY-20260901-ABC12345");

        String secureHash = vnPayService.calculateHmacSha512(params, TEST_HASH_SECRET);

        // Tamper response code to 00
        params.put("vnp_ResponseCode", "00");

        boolean isValid = vnPayService.verifySignature(params, secureHash);
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("verifySignature - Empty and null parameters are safely filtered out")
    void testVerifySignature_EmptyAndNullParams() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "10000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", TEST_TMN_CODE);
        params.put("vnp_TxnRef", "PAY-20260901-ABC12345");
        params.put("vnp_BankTranNo", ""); // empty
        params.put("vnp_CardType", null); // null

        String secureHash = vnPayService.calculateHmacSha512(params, TEST_HASH_SECRET);
        assertThat(secureHash).isNotEmpty();

        boolean isValid = vnPayService.verifySignature(params, secureHash);
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("buildPaymentUrl - Generates complete redirect URL with valid signed hash")
    void testBuildPaymentUrl() {
        Showtime showtime = new Showtime();
        showtime.setId("showtime-1");

        Booking booking = new Booking();
        booking.setId("booking-1");
        booking.setBookingCode("CB-20260901-001");
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalAmount(new BigDecimal("240000.00"));
        booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(5));
        booking.setShowtime(showtime);

        Payment payment = new Payment();
        payment.setId("payment-1");
        payment.setBooking(booking);
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setPaymentCode("PAY-20260901-XYZ12345");
        payment.setAmount(new BigDecimal("240000.00"));
        payment.setPaymentStatus(PaymentStatus.PENDING);

        String paymentUrl = vnPayService.buildPaymentUrl(payment, booking, "192.168.1.100");

        assertThat(paymentUrl).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(paymentUrl).contains("vnp_Amount=24000000"); // 240,000 * 100
        assertThat(paymentUrl).contains("vnp_TmnCode=" + TEST_TMN_CODE);
        assertThat(paymentUrl).contains("vnp_TxnRef=PAY-20260901-XYZ12345");
        assertThat(paymentUrl).contains("vnp_SecureHash=");
    }

    @Test
    @DisplayName("extractClientIp - Handles proxies and headers correctly")
    void testExtractClientIp() {
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");
        assertThat(vnPayService.extractClientIp(request1)).isEqualTo("203.0.113.195");

        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.addHeader("X-Real-IP", "198.51.100.1");
        assertThat(vnPayService.extractClientIp(request2)).isEqualTo("198.51.100.1");

        MockHttpServletRequest request3 = new MockHttpServletRequest();
        request3.setRemoteAddr("192.168.1.50");
        assertThat(vnPayService.extractClientIp(request3)).isEqualTo("192.168.1.50");

        assertThat(vnPayService.extractClientIp(null)).isEqualTo("127.0.0.1");
    }
}

