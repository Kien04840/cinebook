package com.cinebook.security;

import com.cinebook.dto.request.CreateShowtimeRequest;
import com.cinebook.enums.ShowtimeFormat;
import com.cinebook.enums.ShowtimeStatus;
import com.cinebook.service.SeatService;
import com.cinebook.service.ShowtimeSchedulingService;
import com.cinebook.service.ShowtimeService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ShowtimeSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private ShowtimeService showtimeService;

    @MockitoBean
    private ShowtimeSchedulingService schedulingService;

    @MockitoBean
    private SeatService seatService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void publicEndpoints_Anonymous_Allowed() throws Exception {
        mockMvc.perform(get("/api/v1/showtimes"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/showtimes/test-id"))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpoints_Anonymous_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/showtimes"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/showtimes/calendar?cinemaId=c1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/showtimes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/showtimes/generate/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/admin/showtimes/test-id"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void adminEndpoints_CustomerRole_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/showtimes"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/showtimes/calendar?cinemaId=c1"))
                .andExpect(status().isForbidden());

        CreateShowtimeRequest request = CreateShowtimeRequest.builder()
                .movieId("mov-1")
                .auditoriumId("aud-1")
                .format(ShowtimeFormat.IMAX)
                .language("English")
                .startTime(LocalDateTime.of(2026, 9, 1, 10, 0))
                .endTime(LocalDateTime.of(2026, 9, 1, 12, 30))
                .basePrice(new BigDecimal("120000.00"))
                .build();

        mockMvc.perform(post("/api/v1/admin/showtimes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/showtimes/generate/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/admin/showtimes/test-id"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminEndpoints_AdminRole_Allowed() throws Exception {
        mockMvc.perform(get("/api/v1/admin/showtimes"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/showtimes/calendar?cinemaId=c1"))
                .andExpect(status().isOk());

        CreateShowtimeRequest request = CreateShowtimeRequest.builder()
                .movieId("mov-1")
                .auditoriumId("aud-1")
                .format(ShowtimeFormat.IMAX)
                .language("English")
                .startTime(LocalDateTime.of(2026, 9, 1, 10, 0))
                .endTime(LocalDateTime.of(2026, 9, 1, 12, 30))
                .basePrice(new BigDecimal("120000.00"))
                .status(ShowtimeStatus.SCHEDULED)
                .build();

        mockMvc.perform(post("/api/v1/admin/showtimes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/admin/showtimes/test-id"))
                .andExpect(status().isNoContent());
    }
}