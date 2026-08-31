package com.cinebook.security;

import com.cinebook.dto.request.InitiatePaymentRequest;
import com.cinebook.dto.response.InitiatePaymentResponse;
import com.cinebook.dto.response.IpnResponse;
import com.cinebook.dto.response.PaymentResultResponse;
import com.cinebook.dto.response.PaymentSummaryResponse;
import com.cinebook.enums.PaymentMethod;
import com.cinebook.enums.PaymentStatus;
import com.cinebook.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PaymentSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Security: Anonymous user cannot initiate payment -> 401 Unauthorized")
    void anonymous_InitiatePayment_Returns401() throws Exception {
        InitiatePaymentRequest request = new InitiatePaymentRequest(PaymentMethod.VNPAY);

        mockMvc.perform(post("/api/v1/bookings/booking-1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Security: Anonymous user cannot get payment detail -> 401 Unauthorized")
    void anonymous_GetPaymentDetail_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments/payment-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Security: Anonymous user can access VNPay IPN GET -> 200 OK (Public webhook)")
    void anonymous_ProcessIpnGet_Allowed() throws Exception {
        when(paymentService.processIpn(any(Map.class)))
                .thenReturn(new IpnResponse("00", "Confirm Success"));

        mockMvc.perform(get("/api/v1/payments/vnpay/ipn")
                        .param("vnp_TxnRef", "PAY-20260901-ABC")
                        .param("vnp_SecureHash", "hash"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Security: Anonymous user can access VNPay IPN POST -> 200 OK (Public webhook)")
    void anonymous_ProcessIpnPost_Allowed() throws Exception {
        when(paymentService.processIpn(any(Map.class)))
                .thenReturn(new IpnResponse("00", "Confirm Success"));

        mockMvc.perform(post("/api/v1/payments/vnpay/ipn")
                        .param("vnp_TxnRef", "PAY-20260901-ABC")
                        .param("vnp_SecureHash", "hash"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Security: Anonymous user can access VNPay Return GET -> 200 OK (Public redirect)")
    void anonymous_ProcessReturnGet_Allowed() throws Exception {
        when(paymentService.processReturn(any(Map.class)))
                .thenReturn(PaymentResultResponse.builder()
                        .paymentId("p1")
                        .bookingId("b1")
                        .bookingCode("CB-001")
                        .paymentCode("PAY-001")
                        .amount(new BigDecimal("100000.00"))
                        .paymentStatus(PaymentStatus.SUCCESS)
                        .responseCode("00")
                        .message("Giao dịch thành công")
                        .build());

        mockMvc.perform(get("/api/v1/payments/vnpay/return")
                        .param("vnp_TxnRef", "PAY-001")
                        .param("vnp_SecureHash", "hash"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("Security: Authenticated customer can initiate payment -> 200 OK")
    void customerRole_InitiatePayment_Allowed() throws Exception {
        InitiatePaymentRequest request = new InitiatePaymentRequest(PaymentMethod.VNPAY);
        InitiatePaymentResponse response = InitiatePaymentResponse.builder()
                .paymentId("pay-1")
                .paymentCode("PAY-001")
                .amount(new BigDecimal("100000.00"))
                .paymentUrl("http://vnpay.url")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(paymentService.initiatePayment(eq("booking-1"), any(InitiatePaymentRequest.class), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/bookings/booking-1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Security: Admin can initiate payment -> 200 OK")
    void adminRole_InitiatePayment_Allowed() throws Exception {
        InitiatePaymentRequest request = new InitiatePaymentRequest(PaymentMethod.VNPAY);
        InitiatePaymentResponse response = InitiatePaymentResponse.builder()
                .paymentId("pay-1")
                .paymentCode("PAY-001")
                .amount(new BigDecimal("100000.00"))
                .paymentUrl("http://vnpay.url")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(paymentService.initiatePayment(eq("booking-1"), any(InitiatePaymentRequest.class), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/bookings/booking-1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("Security: Authenticated customer can get payment detail -> 200 OK")
    void customerRole_GetPaymentDetail_Allowed() throws Exception {
        PaymentSummaryResponse summary = PaymentSummaryResponse.builder()
                .id("pay-1")
                .paymentCode("PAY-001")
                .amount(new BigDecimal("100000.00"))
                .paymentStatus(PaymentStatus.SUCCESS)
                .build();

        mockMvc.perform(get("/api/v1/payments/pay-1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Security: Anonymous user cannot refund payment -> 401 Unauthorized")
    void anonymous_RefundPayment_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/payments/payment-1/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("Security: Customer cannot access admin refund list -> 403 Forbidden")
    void customerRole_GetAdminRefunds_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/refunds"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Security: Admin can access admin refund list -> 200 OK")
    void adminRole_GetAdminRefunds_Allowed() throws Exception {
        when(paymentService.getAdminRefunds(any(), any()))
                .thenReturn(com.cinebook.dto.response.PageResponse.<com.cinebook.dto.response.RefundResponse>builder()
                        .content(java.util.Collections.emptyList())
                        .build());

        mockMvc.perform(get("/api/v1/admin/refunds"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Security: Admin can refund booking via admin endpoint -> 200 OK")
    void adminRole_RefundBooking_Allowed() throws Exception {
        when(paymentService.refundBooking(eq("booking-1"), any(), any()))
                .thenReturn(com.cinebook.dto.response.RefundResponse.builder()
                        .id("ref-1")
                        .refundStatus(com.cinebook.enums.RefundStatus.SUCCESS)
                        .build());

        mockMvc.perform(post("/api/v1/admin/bookings/booking-1/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}


