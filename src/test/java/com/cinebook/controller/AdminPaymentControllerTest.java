package com.cinebook.controller;

import com.cinebook.dto.request.RefundRequest;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.PaymentSummaryResponse;
import com.cinebook.dto.response.RefundResponse;
import com.cinebook.enums.PaymentStatus;
import com.cinebook.enums.RefundStatus;
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
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminPaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private AdminPaymentController adminPaymentController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminPaymentController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }



    @Test
    @DisplayName("POST /api/v1/admin/bookings/{bookingId}/refund - Admin refunds booking successfully")
    void testRefundBooking_Success() throws Exception {
        RefundResponse response = RefundResponse.builder()
                .id("ref-1")
                .bookingId("booking-1")
                .refundCode("REF-20260901-ADMIN123")
                .refundStatus(RefundStatus.SUCCESS)
                .amount(new BigDecimal("180000.00"))
                .build();

        when(paymentService.refundBooking(eq("booking-1"), any(), any())).thenReturn(response);

        RefundRequest request = new RefundRequest("Admin hoàn tiền do sự cố phòng chiếu");

        mockMvc.perform(post("/api/v1/admin/bookings/booking-1/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ref-1"))
                .andExpect(jsonPath("$.refundCode").value("REF-20260901-ADMIN123"))
                .andExpect(jsonPath("$.refundStatus").value("SUCCESS"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/refunds - Admin gets list of refunds")
    void testGetAdminRefunds_Success() throws Exception {
        RefundResponse response = RefundResponse.builder()
                .id("ref-1")
                .refundCode("REF-123")
                .refundStatus(RefundStatus.SUCCESS)
                .build();

        PageResponse<RefundResponse> pageResponse = PageResponse.<RefundResponse>builder()
                .content(List.of(response))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();


        when(paymentService.getAdminRefunds(any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/refunds?status=SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("ref-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/admin/payments/{id} - Admin gets payment details")
    void testGetAdminPaymentDetail_Success() throws Exception {
        PaymentSummaryResponse summary = PaymentSummaryResponse.builder()
                .id("pay-1")
                .paymentCode("PAY-12345")
                .paymentStatus(PaymentStatus.SUCCESS)
                .build();

        when(paymentService.getPaymentDetail("pay-1")).thenReturn(summary);

        mockMvc.perform(get("/api/v1/admin/payments/pay-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("pay-1"))
                .andExpect(jsonPath("$.paymentCode").value("PAY-12345"));
    }
}
