package com.cinebook.controller;

import com.cinebook.dto.response.AuditoriumDetailResponse;
import com.cinebook.dto.response.SeatResponse;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.AuditoriumService;
import com.cinebook.service.SeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuditoriumControllerTest {

    @Mock
    private AuditoriumService auditoriumService;

    @Mock
    private SeatService seatService;

    @InjectMocks
    private AuditoriumController auditoriumController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(auditoriumController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAuditoriumDetail_Returns200() throws Exception {
        AuditoriumDetailResponse response = AuditoriumDetailResponse.builder()
                .id("aud-1")
                .name("Hall 1")
                .totalSeats(100)
                .build();

        when(auditoriumService.getAuditoriumDetail("aud-1")).thenReturn(response);

        mockMvc.perform(get("/api/v1/auditoriums/aud-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hall 1"));
    }

    @Test
    void getAuditoriumSeats_Returns200() throws Exception {
        SeatResponse seat = SeatResponse.builder()
                .id("seat-1")
                .rowLabel("A")
                .seatNumber((short) 1)
                .seatCode("A1")
                .build();

        when(seatService.getSeatsByAuditorium("aud-1")).thenReturn(List.of(seat));

        mockMvc.perform(get("/api/v1/auditoriums/aud-1/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].seatCode").value("A1"));
    }
}