package com.cinebook.controller;

import com.cinebook.dto.response.AuditoriumResponse;
import com.cinebook.dto.response.CinemaDetailResponse;
import com.cinebook.dto.response.CinemaSummaryResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.enums.CinemaStatus;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.AuditoriumService;
import com.cinebook.service.CinemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CinemaControllerTest {

    @Mock
    private CinemaService cinemaService;

    @Mock
    private AuditoriumService auditoriumService;

    @InjectMocks
    private CinemaController cinemaController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(cinemaController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getPublicCinemas_Returns200() throws Exception {
        CinemaSummaryResponse summary = CinemaSummaryResponse.builder()
                .id("cin-1")
                .name("CineBook Hanoi")
                .city("Hanoi")
                .status(CinemaStatus.ACTIVE)
                .build();

        PageResponse<CinemaSummaryResponse> page = PageResponse.<CinemaSummaryResponse>builder()
                .content(List.of(summary))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(cinemaService.getPublicCinemas(any(), any(), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/cinemas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("CineBook Hanoi"));
    }

    @Test
    void getCinemaDetail_Returns200() throws Exception {
        CinemaDetailResponse detail = CinemaDetailResponse.builder()
                .id("cin-1")
                .name("CineBook Hanoi")
                .city("Hanoi")
                .status(CinemaStatus.ACTIVE)
                .auditoriums(Collections.emptyList())
                .build();

        when(cinemaService.getPublicCinemaDetail("cin-1")).thenReturn(detail);

        mockMvc.perform(get("/api/v1/cinemas/cin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("CineBook Hanoi"));
    }

    @Test
    void getAuditoriumsByCinema_Returns200() throws Exception {
        AuditoriumResponse aud = AuditoriumResponse.builder()
                .id("aud-1")
                .name("Hall 1")
                .type("STANDARD")
                .totalSeats(100)
                .build();

        when(auditoriumService.getAuditoriumsByCinema("cin-1")).thenReturn(List.of(aud));

        mockMvc.perform(get("/api/v1/cinemas/cin-1/auditoriums"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Hall 1"));
    }
}