package com.cinebook.controller;

import com.cinebook.dto.request.CreateAuditoriumRequest;
import com.cinebook.dto.request.CreateCinemaRequest;
import com.cinebook.dto.request.UpdateCinemaRequest;
import com.cinebook.dto.response.*;
import com.cinebook.enums.CinemaStatus;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.AuditoriumService;
import com.cinebook.service.CinemaService;
import com.cinebook.service.ShowtimeSchedulingService;
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
class AdminCinemaControllerTest {

    @Mock
    private CinemaService cinemaService;

    @Mock
    private AuditoriumService auditoriumService;

    @Mock
    private ShowtimeSchedulingService schedulingService;

    @InjectMocks
    private AdminCinemaController adminCinemaController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminCinemaController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAdminCinemas_Returns200() throws Exception {
        PageResponse<CinemaSummaryResponse> page = PageResponse.<CinemaSummaryResponse>builder()
                .content(Collections.emptyList())
                .page(0)
                .size(20)
                .totalElements(0)
                .totalPages(0)
                .build();

        when(cinemaService.getAdminCinemas(any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/cinemas"))
                .andExpect(status().isOk());
    }

    @Test
    void getCinemaSchedulingConfig_Returns200() throws Exception {
        CinemaSchedulingConfigResponse response = CinemaSchedulingConfigResponse.builder()
                .cinemaId("cin-1")
                .cinemaName("CineBook Landmark")
                .build();

        when(schedulingService.getCinemaSchedulingConfig("cin-1")).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/cinemas/cin-1/scheduling-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cinemaId").value("cin-1"));
    }

    @Test
    void createCinema_Returns201() throws Exception {
        CreateCinemaRequest request = CreateCinemaRequest.builder()
                .name("New Cinema")
                .address("123 Street")
                .city("Hanoi")
                .status(CinemaStatus.ACTIVE)
                .build();

        CinemaDetailResponse response = CinemaDetailResponse.builder()
                .id("cin-new")
                .name("New Cinema")
                .city("Hanoi")
                .build();

        when(cinemaService.createCinema(any(CreateCinemaRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/cinemas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Cinema"));
    }

    @Test
    void updateCinema_Returns200() throws Exception {
        UpdateCinemaRequest request = UpdateCinemaRequest.builder()
                .name("Updated Cinema")
                .address("123 Street")
                .city("Hanoi")
                .status(CinemaStatus.ACTIVE)
                .build();

        CinemaDetailResponse response = CinemaDetailResponse.builder()
                .id("cin-1")
                .name("Updated Cinema")
                .city("Hanoi")
                .build();

        when(cinemaService.updateCinema(eq("cin-1"), any(UpdateCinemaRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/cinemas/cin-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Cinema"));
    }

    @Test
    void deleteCinema_Returns204() throws Exception {
        doNothing().when(cinemaService).deleteCinema("cin-1");

        mockMvc.perform(delete("/api/v1/admin/cinemas/cin-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void createAuditorium_Returns201() throws Exception {
        CreateAuditoriumRequest request = CreateAuditoriumRequest.builder()
                .name("Hall A")
                .type("VIP")
                .rowsCount((short) 10)
                .columnsCount((short) 12)
                .build();

        AuditoriumDetailResponse response = AuditoriumDetailResponse.builder()
                .id("aud-1")
                .name("Hall A")
                .totalSeats(120)
                .build();

        when(auditoriumService.createAuditorium(eq("cin-1"), any(CreateAuditoriumRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/cinemas/cin-1/auditoriums")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Hall A"));
    }
}