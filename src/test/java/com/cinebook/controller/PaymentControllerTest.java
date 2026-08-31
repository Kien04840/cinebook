package com.cinebook.controller;

import com.cinebook.dto.request.InitiatePaymentRequest;
import com.cinebook.dto.response.InitiatePaymentResponse;
import com.cinebook.dto.response.IpnResponse;
import com.cinebook.dto.response.PaymentResultResponse;
import com.cinebook.dto.response.PaymentSummaryResponse;
import com.cinebook.enums.PaymentMethod;
import com.cinebook.enums.PaymentStatus;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ForbiddenException;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /api/v1/bookings/{id}/payments - Valid request returns 200 OK with paymentUrl")
    void testInitiatePayment_Success() throws Exception {
        InitiatePaymentRequest request = new InitiatePaymentRequest(PaymentMethod.VNPAY);
        InitiatePaymentResponse response = InitiatePaymentResponse.builder()
                .paymentId("pay-1")
                .paymentCode("PAY-20260901-ABC12345")
                .amount(new BigDecimal("200000.00"))
                .paymentUrl("https://sandbox.vnpayment.vn/vpcpay.html?vnp_Amount=20000000...")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(paymentService.initiatePayment(eq("booking-1"), any(InitiatePaymentRequest.class), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/bookings/booking-1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value("pay-1"))
                .andExpect(jsonPath("$.paymentCode").value("PAY-20260901-ABC12345"))
                .andExpect(jsonPath("$.amount").value(200000.00))
                .andExpect(jsonPath("$.paymentUrl").value("https://sandbox.vnpayment.vn/vpcpay.html?vnp_Amount=20000000..."));
    }

    @Test
    @DisplayName("POST /api/v1/bookings/{id}/payments - Existing PENDING payment returns 409 Conflict")
    void testInitiatePayment_Conflict_Returns409() throws Exception {
        InitiatePaymentRequest request = new InitiatePaymentRequest(PaymentMethod.VNPAY);

        when(paymentService.initiatePayment(eq("booking-1"), any(InitiatePaymentRequest.class), any()))
                .thenThrow(new ConflictException("Đơn đặt vé đang có một phiên thanh toán đang chờ xử lý."));

        mockMvc.perform(post("/api/v1/bookings/booking-1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Đơn đặt vé đang có một phiên thanh toán đang chờ xử lý."));
    }

    @Test
    @DisplayName("GET /api/v1/payments/vnpay/ipn - Returns raw JSON IpnResponse with RspCode 00")
    void testProcessIpnGet_Success() throws Exception {
        IpnResponse response = new IpnResponse("00", "Confirm Success");
        when(paymentService.processIpn(any(Map.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/vnpay/ipn")
                        .param("vnp_TxnRef", "PAY-20260901-ABC12345")
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_SecureHash", "hash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("Confirm Success"));
    }

    @Test
    @DisplayName("POST /api/v1/payments/vnpay/ipn - Returns raw JSON IpnResponse with RspCode 97 on bad signature")
    void testProcessIpnPost_BadSignature() throws Exception {
        IpnResponse response = new IpnResponse("97", "Invalid Checksum");
        when(paymentService.processIpn(any(Map.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/vnpay/ipn")
                        .param("vnp_TxnRef", "PAY-20260901-ABC12345")
                        .param("vnp_SecureHash", "bad_hash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"))
                .andExpect(jsonPath("$.Message").value("Invalid Checksum"));
    }

    @Test
    @DisplayName("GET /api/v1/payments/vnpay/return - Returns 200 OK with PaymentResultResponse")
    void testProcessReturnGet_Success() throws Exception {
        PaymentResultResponse result = PaymentResultResponse.builder()
                .paymentId("pay-1")
                .bookingId("booking-1")
                .bookingCode("CB-20260901-001")
                .paymentCode("PAY-20260901-ABC12345")
                .amount(new BigDecimal("200000.00"))
                .paymentStatus(PaymentStatus.SUCCESS)
                .responseCode("00")
                .message("Giao dịch thanh toán thành công.")
                .build();

        when(paymentService.processReturn(any(Map.class))).thenReturn(result);

        mockMvc.perform(get("/api/v1/payments/vnpay/return")
                        .param("vnp_TxnRef", "PAY-20260901-ABC12345")
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_SecureHash", "valid_hash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value("pay-1"))
                .andExpect(jsonPath("$.bookingCode").value("CB-20260901-001"))
                .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Giao dịch thanh toán thành công."));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id} - Returns 200 OK with PaymentSummaryResponse")
    void testGetPaymentDetail_Success() throws Exception {
        PaymentSummaryResponse summary = PaymentSummaryResponse.builder()
                .id("pay-1")
                .paymentCode("PAY-20260901-ABC12345")
                .amount(new BigDecimal("200000.00"))
                .paymentStatus(PaymentStatus.SUCCESS)
                .build();

        when(paymentService.getPaymentDetail("pay-1")).thenReturn(summary);

        mockMvc.perform(get("/api/v1/payments/pay-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("pay-1"))
                .andExpect(jsonPath("$.paymentCode").value("PAY-20260901-ABC12345"))
                .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id} - Non-existent ID returns 404 Not Found")
    void testGetPaymentDetail_NotFound_Returns404() throws Exception {
        when(paymentService.getPaymentDetail("non-existent"))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy thông tin thanh toán"));

        mockMvc.perform(get("/api/v1/payments/non-existent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy thông tin thanh toán"));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id} - Other customer access returns 403 Forbidden")
    void testGetPaymentDetail_Forbidden_Returns403() throws Exception {
        when(paymentService.getPaymentDetail("pay-1"))
                .thenThrow(new ForbiddenException("Bạn không có quyền thao tác với thanh toán"));

        mockMvc.perform(get("/api/v1/payments/pay-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền thao tác với thanh toán"));
    }

    @Test
    @DisplayName("POST /api/v1/payments/{paymentId}/refund - Success returns 200 OK with RefundResponse")
    void testRefundPayment_Success() throws Exception {
        com.cinebook.dto.response.RefundResponse refundResponse = com.cinebook.dto.response.RefundResponse.builder()
                .id("ref-1")
                .paymentId("pay-1")
                .refundCode("REF-20260901-ABCD1234")
                .refundStatus(com.cinebook.enums.RefundStatus.SUCCESS)
                .amount(new BigDecimal("200000.00"))
                .build();

        when(paymentService.refundPayment(eq("pay-1"), any(), any())).thenReturn(refundResponse);

        com.cinebook.dto.request.RefundRequest request = new com.cinebook.dto.request.RefundRequest("Khách hàng bận đột xuất");

        mockMvc.perform(post("/api/v1/payments/pay-1/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ref-1"))
                .andExpect(jsonPath("$.refundCode").value("REF-20260901-ABCD1234"))
                .andExpect(jsonPath("$.refundStatus").value("SUCCESS"));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{paymentId}/refund - Success returns 200 OK")
    void testGetRefundDetail_Success() throws Exception {
        com.cinebook.dto.response.RefundResponse refundResponse = com.cinebook.dto.response.RefundResponse.builder()
                .id("ref-1")
                .paymentId("pay-1")
                .refundCode("REF-20260901-ABCD1234")
                .refundStatus(com.cinebook.enums.RefundStatus.SUCCESS)
                .build();

        when(paymentService.getRefundDetail("pay-1")).thenReturn(refundResponse);

        mockMvc.perform(get("/api/v1/payments/pay-1/refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ref-1"))
                .andExpect(jsonPath("$.refundCode").value("REF-20260901-ABCD1234"));
    }
}


