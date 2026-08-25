package com.cinebook.controller;

import com.cinebook.dto.request.BatchUpdateSeatTypeRequest;
import com.cinebook.dto.request.UpdateAuditoriumRequest;
import com.cinebook.dto.request.UpdateSeatStatusRequest;
import com.cinebook.dto.request.UpdateSeatTypeForSeatRequest;
import com.cinebook.dto.response.AuditoriumResponse;
import com.cinebook.dto.response.SeatResponse;
import com.cinebook.enums.AuditoriumStatus;
import com.cinebook.enums.SeatStatus;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.AuditoriumService;
import com.cinebook.service.SeatService;
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
}