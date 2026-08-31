package com.cinebook.controller;

import com.cinebook.dto.request.CancelBookingRequest;
import com.cinebook.dto.request.CreateBookingRequest;
import com.cinebook.dto.response.*;
import com.cinebook.enums.BookingStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ForbiddenException;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingController bookingController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(bookingController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void createBooking_ValidRequest_Returns201() throws Exception {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("seat-1", "seat-2"))
                .build();

        BookingDetailResponse response = BookingDetailResponse.builder()
                .id("booking-1")
                .bookingCode("CB-20260901-ABC123")
                .bookingStatus(BookingStatus.PENDING_PAYMENT)
                .totalAmount(new BigDecimal("220000.00"))
                .holdExpiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .build();

        when(bookingService.createBooking(any(CreateBookingRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("booking-1"))
                .andExpect(jsonPath("$.bookingCode").value("CB-20260901-ABC123"))
                .andExpect(jsonPath("$.bookingStatus").value("PENDING_PAYMENT"));
    }

    @Test
    void createBooking_MissingShowtime_Returns400() throws Exception {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId("")
                .seatIds(List.of("seat-1"))
                .build();

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBooking_Conflict_Returns409() throws Exception {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("seat-1"))
                .build();

        when(bookingService.createBooking(any(CreateBookingRequest.class)))
                .thenThrow(new ConflictException("Ghế đã được giữ chỗ bởi người khác."));

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ghế đã được giữ chỗ bởi người khác."));
    }

    @Test
    void getMyBookings_Returns200() throws Exception {
        BookingSummaryResponse summary = BookingSummaryResponse.builder()
                .id("booking-1")
                .bookingCode("CB-20260901-ABC123")
                .bookingStatus(BookingStatus.PENDING_PAYMENT)
                .totalAmount(new BigDecimal("100000.00"))
                .seatCount(1)
                .build();

        PageResponse<BookingSummaryResponse> page = PageResponse.<BookingSummaryResponse>builder()
                .content(List.of(summary))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(bookingService.getMyBookings(any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/bookings/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("booking-1"))
                .andExpect(jsonPath("$.content[0].bookingCode").value("CB-20260901-ABC123"));
    }

    @Test
    void getBookingDetail_Owner_Returns200() throws Exception {
        BookingDetailResponse response = BookingDetailResponse.builder()
                .id("booking-1")
                .bookingCode("CB-20260901-ABC123")
                .bookingStatus(BookingStatus.PENDING_PAYMENT)
                .totalAmount(new BigDecimal("100000.00"))
                .build();

        when(bookingService.getBookingDetail("booking-1")).thenReturn(response);

        mockMvc.perform(get("/api/v1/bookings/booking-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("booking-1"));
    }

    @Test
    void getBookingDetail_NotOwner_Returns403() throws Exception {
        when(bookingService.getBookingDetail("booking-1"))
                .thenThrow(new ForbiddenException("Bạn không có quyền truy cập đơn đặt vé này."));

        mockMvc.perform(get("/api/v1/bookings/booking-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập đơn đặt vé này."));
    }

    @Test
    void cancelBooking_Pending_Returns200() throws Exception {
        CancelBookingRequest request = CancelBookingRequest.builder().reason("Change my mind").build();
        BookingDetailResponse response = BookingDetailResponse.builder()
                .id("booking-1")
                .bookingStatus(BookingStatus.CANCELLED)
                .build();

        when(bookingService.cancelBooking(eq("booking-1"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/bookings/booking-1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("CANCELLED"));
    }

    @Test
    void cancelBooking_Paid_Returns400() throws Exception {
        when(bookingService.cancelBooking(eq("booking-1"), any()))
                .thenThrow(new BadRequestException("Không thể tự hủy đơn đặt vé đã thanh toán thành công."));

        mockMvc.perform(post("/api/v1/bookings/booking-1/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Không thể tự hủy đơn đặt vé đã thanh toán thành công."));
    }
}

