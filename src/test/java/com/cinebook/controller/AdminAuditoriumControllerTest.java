package com.cinebook.controller;

import com.cinebook.dto.request.BatchUpdateSeatTypeRequest;
import com.cinebook.dto.request.UpdateAuditoriumRequest;
import com.cinebook.dto.request.UpdateSeatStatusRequest;
import com.cinebook.dto.request.UpdateSeatTypeForSeatRequest;
import com.cinebook.dto.response.AuditoriumAvailabilityResponse;
import com.cinebook.dto.response.AuditoriumResponse;
import com.cinebook.dto.response.SeatResponse;
import com.cinebook.enums.AuditoriumStatus;
import com.cinebook.enums.SeatStatus;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.AuditoriumService;
import com.cinebook.service.SeatService;
import com.cinebook.service.ShowtimeSchedulingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminAuditoriumControllerTest {

    @Mock
    private AuditoriumService auditoriumService;

    @Mock
    private SeatService seatService;

    @Mock
    private ShowtimeSchedulingService schedulingService;

    @InjectMocks
    private AdminAuditoriumController adminAuditoriumController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminAuditoriumController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAuditoriumAvailability_Returns200() throws Exception {
        AuditoriumAvailabilityResponse response = AuditoriumAvailabilityResponse.builder()
                .auditoriumId("aud-1")
                .auditoriumName("Hall 1")
                .build();

        when(schedulingService.getAuditoriumAvailability(eq("aud-1"), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/auditoriums/aud-1/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditoriumId").value("aud-1"));
    }

    @Test
    void updateAuditorium_Returns200() throws Exception {
        UpdateAuditoriumRequest request = UpdateAuditoriumRequest.builder()
                .name("Hall 1 Updated")
                .type("VIP")
                .status(AuditoriumStatus.ACTIVE)
                .build();

        AuditoriumResponse response = AuditoriumResponse.builder()
                .id("aud-1")
                .name("Hall 1 Updated")
                .build();

        when(auditoriumService.updateAuditorium(eq("aud-1"), any(UpdateAuditoriumRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/auditoriums/aud-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hall 1 Updated"));
    }

    @Test
    void deleteAuditorium_Returns204() throws Exception {
        doNothing().when(auditoriumService).deleteAuditorium("aud-1");

        mockMvc.perform(delete("/api/v1/admin/auditoriums/aud-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void updateSeatType_Returns200() throws Exception {
        UpdateSeatTypeForSeatRequest request = UpdateSeatTypeForSeatRequest.builder()
                .seatTypeId("st-vip")
                .build();

        SeatResponse response = SeatResponse.builder()
                .id("seat-1")
                .seatTypeName("VIP")
                .build();

        when(seatService.updateSeatType("seat-1", "st-vip")).thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/auditoriums/aud-1/seats/seat-1/seat-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatTypeName").value("VIP"));
    }

    @Test
    void batchUpdateSeatType_Returns200() throws Exception {
        BatchUpdateSeatTypeRequest request = BatchUpdateSeatTypeRequest.builder()
                .seatIds(List.of("seat-1", "seat-2"))
                .seatTypeId("st-vip")
                .build();

        when(seatService.batchUpdateSeatType(eq("aud-1"), any(), eq("st-vip"))).thenReturn(List.of(
                SeatResponse.builder().id("seat-1").seatTypeName("VIP").build()
        ));

        mockMvc.perform(put("/api/v1/admin/auditoriums/aud-1/seats/batch-seat-type")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void updateSeatStatus_Returns200() throws Exception {
        UpdateSeatStatusRequest request = UpdateSeatStatusRequest.builder()
                .status(SeatStatus.BROKEN)
                .build();

        SeatResponse response = SeatResponse.builder()
                .id("seat-1")
                .status(SeatStatus.BROKEN)
                .build();

        when(seatService.updateSeatStatus("seat-1", SeatStatus.BROKEN)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/admin/auditoriums/aud-1/seats/seat-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BROKEN"));
    }

    @Test
    void previewBatchUpdateSeatType_Returns200() throws Exception {
        BatchUpdateSeatTypeRequest request = BatchUpdateSeatTypeRequest.builder()
                .seatIds(List.of("seat-1"))
                .seatTypeId("st-couple")
                .build();

        com.cinebook.dto.response.BatchUpdateSeatTypePreviewResponse response =
                com.cinebook.dto.response.BatchUpdateSeatTypePreviewResponse.builder()
                        .seatCount(1)
                        .isValid(true)
                        .targetSeatTypeName("Couple")
                        .willDeleteSeatCodes(List.of("E2"))
                        .build();

        when(seatService.previewBatchUpdateSeatType(eq("aud-1"), any(), eq("st-couple"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/auditoriums/aud-1/seats/batch-seat-type/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid").value(true))
                .andExpect(jsonPath("$.willDeleteSeatCodes[0]").value("E2"));
    }

    @Test
    void batchUpdateSeatStatus_Returns200() throws Exception {
        com.cinebook.dto.request.BatchUpdateSeatStatusRequest request =
                com.cinebook.dto.request.BatchUpdateSeatStatusRequest.builder()
                        .seatIds(List.of("seat-1", "seat-2"))
                        .status(SeatStatus.BROKEN)
                        .build();

        when(seatService.batchUpdateSeatStatus(eq("aud-1"), any(), eq(SeatStatus.BROKEN))).thenReturn(List.of(
                SeatResponse.builder().id("seat-1").status(SeatStatus.BROKEN).build(),
                SeatResponse.builder().id("seat-2").status(SeatStatus.BROKEN).build()
        ));

        mockMvc.perform(patch("/api/v1/admin/auditoriums/aud-1/seats/batch-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("BROKEN"));
    }

    @Test
    void resetAuditoriumLayout_Returns200() throws Exception {
        com.cinebook.dto.response.AuditoriumDetailResponse response = com.cinebook.dto.response.AuditoriumDetailResponse.builder()
                .id("aud-1")
                .name("Hall 1")
                .totalSeats(36)
                .canModifyLayout(true)
                .build();

        when(auditoriumService.resetAuditoriumLayout("aud-1")).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/auditoriums/aud-1/reset-layout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("aud-1"))
                .andExpect(jsonPath("$.totalSeats").value(36));
    }

    @Test
    void normalizeEmptyLayouts_Returns200() throws Exception {
        com.cinebook.dto.response.NormalizeEmptyLayoutsResponse response = com.cinebook.dto.response.NormalizeEmptyLayoutsResponse.builder()
                .processedCount(10)
                .scannedCount(10)
                .updatedCount(8)
                .normalizedCount(8)
                .unchangedCount(2)
                .skippedCount(2)
                .build();

        when(auditoriumService.normalizeEmptyAuditoriumsLayout()).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/auditoriums/normalize-empty-layouts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount").value(10))
                .andExpect(jsonPath("$.scannedCount").value(10))
                .andExpect(jsonPath("$.updatedCount").value(8))
                .andExpect(jsonPath("$.normalizedCount").value(8))
                .andExpect(jsonPath("$.unchangedCount").value(2))
                .andExpect(jsonPath("$.skippedCount").value(2));
    }
}