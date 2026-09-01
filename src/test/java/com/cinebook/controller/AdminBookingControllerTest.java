package com.cinebook.controller;

import com.cinebook.dto.request.CancelBookingRequest;
import com.cinebook.dto.response.BookingDetailResponse;
import com.cinebook.dto.response.BookingSummaryResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.ShowtimeSummaryResponse;
import com.cinebook.enums.BookingStatus;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminBookingControllerTest {

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private AdminBookingController adminBookingController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminBookingController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void getAdminBookings_Returns200() throws Exception {
        BookingSummaryResponse summary = BookingSummaryResponse.builder()
                .id("b-1")
                .bookingCode("CB-20260901-ABCDEF")
                .bookingStatus(BookingStatus.PAID)
                .totalAmount(new BigDecimal("180000"))
                .seatCount(2)
                .showtime(ShowtimeSummaryResponse.builder()
                        .movieTitle("Mai")
                        .cinemaName("CineBook Vincom")
                        .build())
                .createdAt(LocalDateTime.now())
                .build();

        PageResponse<BookingSummaryResponse> page = PageResponse.of(
                new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1),
                s -> s
        );

        when(bookingService.getAdminBookings(any(), any(), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/bookings")
                        .param("q", "ABCDEF")
                        .param("status", "PAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].bookingCode").value("CB-20260901-ABCDEF"))
                .andExpect(jsonPath("$.content[0].bookingStatus").value("PAID"));
    }

    @Test
    void getAdminBookingDetail_Returns200() throws Exception {
        BookingDetailResponse detail = BookingDetailResponse.builder()
                .id("b-1")
                .bookingCode("CB-20260901-ABCDEF")
                .bookingStatus(BookingStatus.PAID)
                .totalAmount(new BigDecimal("180000"))
                .grossAmount(new BigDecimal("180000"))
                .discountAmount(BigDecimal.ZERO)
                .seats(List.of())
                .tickets(List.of())
                .payments(List.of())
                .createdAt(LocalDateTime.now())
                .build();

        when(bookingService.getBookingDetail("b-1")).thenReturn(detail);

        mockMvc.perform(get("/api/v1/admin/bookings/b-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("b-1"))
                .andExpect(jsonPath("$.bookingCode").value("CB-20260901-ABCDEF"));
    }

    @Test
    void cancelAdminBooking_Returns200() throws Exception {
        CancelBookingRequest request = new CancelBookingRequest();
        request.setReason("Admin cancellation");

        BookingDetailResponse detail = BookingDetailResponse.builder()
                .id("b-1")
                .bookingCode("CB-20260901-ABCDEF")
                .bookingStatus(BookingStatus.CANCELLED)
                .totalAmount(new BigDecimal("180000"))
                .grossAmount(new BigDecimal("180000"))
                .discountAmount(BigDecimal.ZERO)
                .cancelledReason("Admin cancellation")
                .createdAt(LocalDateTime.now())
                .build();

        when(bookingService.cancelBooking(eq("b-1"), any(CancelBookingRequest.class))).thenReturn(detail);

        mockMvc.perform(post("/api/v1/admin/bookings/b-1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("CANCELLED"));
    }
}