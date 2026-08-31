package com.cinebook.security;

import com.cinebook.dto.request.CreateBookingRequest;
import com.cinebook.dto.response.BookingDetailResponse;
import com.cinebook.dto.response.ShowtimeSeatStatusResponse;
import com.cinebook.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class BookingSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void anonymous_CreateBooking_Returns401() throws Exception {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("seat-1"))
                .build();

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymous_GetMyBookings_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymous_GetBookingDetail_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/booking-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymous_CancelBooking_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/booking-1/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void public_GetShowtimeSeats_Anonymous_Allowed() throws Exception {
        when(bookingService.getShowtimeSeatAvailability("showtime-1")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/showtimes/showtime-1/seats"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "customer@cinebook.com", roles = {"CUSTOMER"})
    void customer_CreateBooking_AllowedBySecurity() throws Exception {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .showtimeId("showtime-1")
                .seatIds(List.of("seat-1"))
                .build();

        BookingDetailResponse response = BookingDetailResponse.builder()
                .id("booking-1")
                .build();

        when(bookingService.createBooking(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}

