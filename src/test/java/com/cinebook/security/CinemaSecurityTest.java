package com.cinebook.security;

import com.cinebook.dto.request.CreateCinemaRequest;
import com.cinebook.dto.request.CreateSeatTypeRequest;
import com.cinebook.enums.CinemaStatus;
import com.cinebook.enums.SeatTypeStatus;
import com.cinebook.service.AuditoriumService;
import com.cinebook.service.CinemaService;
import com.cinebook.service.SeatService;
import com.cinebook.service.SeatTypeService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class CinemaSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private CinemaService cinemaService;

    @MockitoBean
    private AuditoriumService auditoriumService;

    @MockitoBean
    private SeatService seatService;

    @MockitoBean
    private SeatTypeService seatTypeService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void publicEndpoints_Anonymous_Allowed() throws Exception {
        mockMvc.perform(get("/api/v1/cinemas"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/cinemas/test-id"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/cinemas/test-id/auditoriums"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auditoriums/test-id"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auditoriums/test-id/seats"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/seat-types"))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpoints_Anonymous_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/cinemas"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/cinemas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/admin/cinemas/test-id"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/seat-types"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void adminEndpoints_CustomerRole_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/cinemas"))
                .andExpect(status().isForbidden());

        CreateCinemaRequest request = CreateCinemaRequest.builder()
                .name("Cinema A")
                .address("123 Street")
                .city("Hanoi")
                .status(CinemaStatus.ACTIVE)
                .build();

        mockMvc.perform(post("/api/v1/admin/cinemas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/admin/cinemas/test-id"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/seat-types"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminEndpoints_AdminRole_Allowed() throws Exception {
        mockMvc.perform(get("/api/v1/admin/cinemas"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/seat-types"))
                .andExpect(status().isOk());

        CreateSeatTypeRequest seatTypeRequest = CreateSeatTypeRequest.builder()
                .name("VIP")
                .priceModifier(new BigDecimal("20000.00"))
                .status(SeatTypeStatus.ACTIVE)
                .build();

        mockMvc.perform(post("/api/v1/admin/seat-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seatTypeRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/admin/cinemas/test-id"))
                .andExpect(status().isNoContent());
    }
}