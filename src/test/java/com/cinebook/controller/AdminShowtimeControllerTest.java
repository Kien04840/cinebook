package com.cinebook.controller;

import com.cinebook.dto.request.*;
import com.cinebook.dto.response.*;
import com.cinebook.enums.ShowtimeFormat;
import com.cinebook.enums.ShowtimeStatus;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.ShowtimeSchedulingService;
import com.cinebook.service.ShowtimeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminShowtimeControllerTest {

    @Mock
    private ShowtimeService showtimeService;

    @Mock
    private ShowtimeSchedulingService schedulingService;

    @InjectMocks
    private AdminShowtimeController adminShowtimeController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminShowtimeController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAdminShowtimes_Returns200() throws Exception {
        PageResponse<ShowtimeSummaryResponse> page = PageResponse.<ShowtimeSummaryResponse>builder()
                .content(Collections.emptyList())
                .page(0)
                .size(20)
                .totalElements(0)
                .totalPages(0)
                .build();

        when(showtimeService.getAdminShowtimes(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/showtimes"))
                .andExpect(status().isOk());
    }

    @Test
    void getCalendarSchedule_Returns200() throws Exception {
        CalendarScheduleResponse response = CalendarScheduleResponse.builder()
                .cinemaId("cin-1")
                .cinemaName("CineBook Landmark")
                .from(LocalDate.of(2026, 9, 1))
                .to(LocalDate.of(2026, 9, 7))
                .auditoriums(Collections.emptyList())
                .build();

        when(schedulingService.getCalendarSchedule(eq("cin-1"), any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/showtimes/calendar?cinemaId=cin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cinemaId").value("cin-1"))
                .andExpect(jsonPath("$.cinemaName").value("CineBook Landmark"));
    }

    @Test
    void getCinemaSchedulingConfig_Returns200() throws Exception {
        CinemaSchedulingConfigResponse response = CinemaSchedulingConfigResponse.builder()
                .cinemaId("cin-1")
                .cinemaName("CineBook Landmark")
                .build();

        when(schedulingService.getCinemaSchedulingConfig("cin-1")).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/showtimes/scheduling-config?cinemaId=cin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cinemaId").value("cin-1"));
    }

    @Test
    void getAuditoriumAvailability_Returns200() throws Exception {
        AuditoriumAvailabilityResponse response = AuditoriumAvailabilityResponse.builder()
                .auditoriumId("aud-1")
                .auditoriumName("Hall 1")
                .build();

        when(schedulingService.getAuditoriumAvailability(eq("aud-1"), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/showtimes/auditorium-availability?auditoriumId=aud-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditoriumId").value("aud-1"));
    }

    @Test
    void createShowtime_Returns201() throws Exception {
        CreateShowtimeRequest request = CreateShowtimeRequest.builder()
                .movieId("mov-1")
                .auditoriumId("aud-1")
                .format(ShowtimeFormat.IMAX)
                .language("English")
                .startTime(LocalDateTime.of(2026, 9, 1, 10, 0))
                .basePrice(new BigDecimal("120000.00"))
                .build();

        ShowtimeDetailResponse response = ShowtimeDetailResponse.builder()
                .id("st-new")
                .movie(MovieSummaryResponse.builder().title("Inception").build())
                .format(ShowtimeFormat.IMAX)
                .status(ShowtimeStatus.SCHEDULED)
                .build();

        when(showtimeService.createShowtime(any(CreateShowtimeRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/showtimes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("st-new"))
                .andExpect(jsonPath("$.movie.title").value("Inception"));
    }

    @Test
    void validateSingleSlot_Returns200() throws Exception {
        ValidateShowtimeSlotRequest request = ValidateShowtimeSlotRequest.builder()
                .movieId("mov-1")
                .auditoriumId("aud-1")
                .startTime(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();

        ValidateShowtimeSlotResponse response = ValidateShowtimeSlotResponse.builder()
                .valid(true)
                .calculatedStartTime(LocalDateTime.of(2026, 9, 1, 10, 0))
                .calculatedEndTime(LocalDateTime.of(2026, 9, 1, 12, 28))
                .build();

        when(schedulingService.validateSingleSlot(any(ValidateShowtimeSlotRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/showtimes/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void suggestNextSlot_Returns200() throws Exception {
        SuggestShowtimeSlotRequest request = SuggestShowtimeSlotRequest.builder()
                .movieId("mov-1")
                .auditoriumId("aud-1")
                .requestedStartTime(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();

        SuggestShowtimeSlotResponse response = SuggestShowtimeSlotResponse.builder()
                .available(true)
                .suggestedStartTime(LocalDateTime.of(2026, 9, 1, 10, 15))
                .build();

        when(schedulingService.suggestNextSlot(any(SuggestShowtimeSlotRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/showtimes/suggest-next-slot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void previewGeneration_Returns200() throws Exception {
        ShowtimeGenerationRequest request = ShowtimeGenerationRequest.builder()
                .movieId("mov-1")
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 1))
                .build();

        ShowtimeGenerationPreviewResponse response = ShowtimeGenerationPreviewResponse.builder()
                .totalProposed(5)
                .totalValid(5)
                .totalConflicted(0)
                .slots(Collections.emptyList())
                .build();

        when(schedulingService.previewGeneration(any(ShowtimeGenerationRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/showtimes/generate/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProposed").value(5))
                .andExpect(jsonPath("$.totalValid").value(5));
    }

    @Test
    void generateShowtimes_Returns201() throws Exception {
        ShowtimeGenerationRequest request = ShowtimeGenerationRequest.builder()
                .movieId("mov-1")
                .auditoriumIds(List.of("aud-1"))
                .startDate(LocalDate.of(2026, 9, 1))
                .build();

        ShowtimeGenerationResultResponse response = ShowtimeGenerationResultResponse.builder()
                .totalCreated(5)
                .totalSkipped(0)
                .totalConflicted(0)
                .createdShowtimes(Collections.emptyList())
                .build();

        when(schedulingService.generateShowtimes(any(ShowtimeGenerationRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/showtimes/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCreated").value(5));
    }

    @Test
    void copySchedule_Returns200() throws Exception {
        CopyScheduleRequest request = CopyScheduleRequest.builder()
                .sourceDate(LocalDate.of(2026, 9, 1))
                .targetDate(LocalDate.of(2026, 9, 2))
                .cinemaId("cin-1")
                .build();

        CopyScheduleResultResponse response = CopyScheduleResultResponse.builder()
                .totalCopied(4)
                .totalSkipped(0)
                .totalConflicted(0)
                .createdShowtimes(Collections.emptyList())
                .build();

        when(schedulingService.copySchedule(any(CopyScheduleRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/showtimes/copy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCopied").value(4));
    }

    @Test
    void updateShowtime_Returns200() throws Exception {
        UpdateShowtimeRequest request = UpdateShowtimeRequest.builder()
                .movieId("mov-1")
                .auditoriumId("aud-1")
                .format(ShowtimeFormat.THREE_D)
                .language("English")
                .startTime(LocalDateTime.of(2026, 9, 1, 10, 0))
                .basePrice(new BigDecimal("150000.00"))
                .status(ShowtimeStatus.SCHEDULED)
                .build();

        ShowtimeDetailResponse response = ShowtimeDetailResponse.builder()
                .id("st-1")
                .movie(MovieSummaryResponse.builder().title("Inception").build())
                .format(ShowtimeFormat.THREE_D)
                .status(ShowtimeStatus.SCHEDULED)
                .build();

        when(showtimeService.updateShowtime(eq("st-1"), any(UpdateShowtimeRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/showtimes/st-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("st-1"));
    }

    @Test
    void deleteShowtime_Returns204() throws Exception {
        doNothing().when(showtimeService).deleteShowtime("st-1");

        mockMvc.perform(delete("/api/v1/admin/showtimes/st-1"))
                .andExpect(status().isNoContent());
    }
}
