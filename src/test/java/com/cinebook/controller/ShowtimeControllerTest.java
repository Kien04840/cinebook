package com.cinebook.controller;

import com.cinebook.dto.response.AuditoriumResponse;
import com.cinebook.dto.response.MovieSummaryResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.SeatResponse;
import com.cinebook.dto.response.ShowtimeDetailResponse;
import com.cinebook.dto.response.ShowtimeSummaryResponse;
import com.cinebook.enums.ShowtimeFormat;
import com.cinebook.enums.ShowtimeStatus;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.SeatService;
import com.cinebook.service.ShowtimeService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ShowtimeControllerTest {

    @Mock
    private ShowtimeService showtimeService;

    @Mock
    private SeatService seatService;

    @InjectMocks
    private ShowtimeController showtimeController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(showtimeController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getPublicShowtimes_Returns200() throws Exception {
        ShowtimeSummaryResponse summary = ShowtimeSummaryResponse.builder()
                .id("st-1")
                .movieTitle("Inception")
                .format(ShowtimeFormat.IMAX)
                .basePrice(new BigDecimal("120000.00"))
                .status(ShowtimeStatus.SCHEDULED)
                .build();

        PageResponse<ShowtimeSummaryResponse> page = PageResponse.<ShowtimeSummaryResponse>builder()
                .content(List.of(summary))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(showtimeService.getPublicShowtimes(any(), any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/showtimes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].movieTitle").value("Inception"));
    }

    @Test
    void getPublicShowtimeDetail_Returns200() throws Exception {
        ShowtimeDetailResponse detail = ShowtimeDetailResponse.builder()
                .id("st-1")
                .movie(MovieSummaryResponse.builder().id("mov-1").title("Inception").build())
                .auditorium(AuditoriumResponse.builder().id("aud-1").name("Hall 1").build())
                .format(ShowtimeFormat.IMAX)
                .startTime(LocalDateTime.of(2026, 9, 1, 10, 0))
                .endTime(LocalDateTime.of(2026, 9, 1, 12, 30))
                .basePrice(new BigDecimal("120000.00"))
                .status(ShowtimeStatus.SCHEDULED)
                .build();

        when(showtimeService.getPublicShowtimeDetail("st-1")).thenReturn(detail);

        mockMvc.perform(get("/api/v1/showtimes/st-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movie.title").value("Inception"));
    }

    @Test
    void getShowtimeSeats_Returns200() throws Exception {
        ShowtimeDetailResponse detail = ShowtimeDetailResponse.builder()
                .id("st-1")
                .auditorium(AuditoriumResponse.builder().id("aud-1").name("Hall 1").build())
                .build();

        SeatResponse seat = SeatResponse.builder()
                .id("seat-1")
                .auditoriumId("aud-1")
                .seatCode("A1")
                .build();

        when(showtimeService.getPublicShowtimeDetail("st-1")).thenReturn(detail);
        when(seatService.getSeatsByAuditorium("aud-1")).thenReturn(List.of(seat));

        mockMvc.perform(get("/api/v1/showtimes/st-1/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].seatCode").value("A1"));
    }
}