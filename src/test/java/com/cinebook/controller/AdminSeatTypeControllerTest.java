package com.cinebook.controller;

import com.cinebook.dto.request.CreateSeatTypeRequest;
import com.cinebook.dto.request.UpdateSeatTypeRequest;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.SeatTypeResponse;
import com.cinebook.enums.SeatTypeStatus;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.SeatTypeService;
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

import java.math.BigDecimal;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminSeatTypeControllerTest {

    @Mock
    private SeatTypeService seatTypeService;

    @InjectMocks
    private AdminSeatTypeController adminSeatTypeController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminSeatTypeController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAdminSeatTypes_Returns200() throws Exception {
        PageResponse<SeatTypeResponse> page = PageResponse.<SeatTypeResponse>builder()
                .content(Collections.emptyList())
                .page(0)
                .size(20)
                .totalElements(0)
                .totalPages(0)
                .build();

        when(seatTypeService.getAdminSeatTypes(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/seat-types"))
                .andExpect(status().isOk());
    }

    @Test
    void createSeatType_Returns201() throws Exception {
        CreateSeatTypeRequest request = CreateSeatTypeRequest.builder()
                .name("VIP")
                .priceModifier(new BigDecimal("20000.00"))
                .status(SeatTypeStatus.ACTIVE)
                .build();

        SeatTypeResponse response = SeatTypeResponse.builder()
                .id("st-vip")
                .name("VIP")
                .priceModifier(new BigDecimal("20000.00"))
                .build();

        when(seatTypeService.createSeatType(any(CreateSeatTypeRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/seat-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("VIP"));
    }

    @Test
    void updateSeatType_Returns200() throws Exception {
        UpdateSeatTypeRequest request = UpdateSeatTypeRequest.builder()
                .name("VIP Updated")
                .priceModifier(new BigDecimal("25000.00"))
                .status(SeatTypeStatus.ACTIVE)
                .build();

        SeatTypeResponse response = SeatTypeResponse.builder()
                .id("st-vip")
                .name("VIP Updated")
                .build();

        when(seatTypeService.updateSeatType(eq("st-vip"), any(UpdateSeatTypeRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/seat-types/st-vip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("VIP Updated"));
    }
}