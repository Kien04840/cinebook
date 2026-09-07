package com.cinebook.controller;

import com.cinebook.dto.request.CreateTimeSlotPricingRuleRequest;
import com.cinebook.dto.request.UpdateTimeSlotPricingRuleRequest;
import com.cinebook.dto.response.TimeSlotPricingRuleResponse;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.PricingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminPricingControllerTest {

    @Mock
    private PricingService pricingService;

    @InjectMocks
    private AdminPricingController adminPricingController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private TimeSlotPricingRuleResponse sampleRule;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminPricingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        sampleRule = TimeSlotPricingRuleResponse.builder()
                .id("ts-1")
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(22, 0))
                .modifier(new BigDecimal("15000.00"))
                .build();
    }

    @Test
    void getTimeSlots_AliasEndpoint_Returns200AndComputedName() throws Exception {
        when(pricingService.getAllTimeSlotPricingRules()).thenReturn(List.of(sampleRule));

        mockMvc.perform(get("/api/v1/admin/pricing/time-slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("ts-1"))
                .andExpect(jsonPath("$[0].name").value("18:00 - 22:00"))
                .andExpect(jsonPath("$[0].modifier").value(15000.00));
    }

    @Test
    void getTimeSlotRules_OriginalEndpoint_Returns200() throws Exception {
        when(pricingService.getAllTimeSlotPricingRules()).thenReturn(List.of(sampleRule));

        mockMvc.perform(get("/api/v1/admin/pricing/time-slot-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("ts-1"))
                .andExpect(jsonPath("$[0].name").value("18:00 - 22:00"));
    }

    @Test
    void createTimeSlot_Success() throws Exception {
        CreateTimeSlotPricingRuleRequest request = CreateTimeSlotPricingRuleRequest.builder()
                .name("Suất tối")
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(22, 0))
                .modifier(new BigDecimal("15000.00"))
                .build();

        when(pricingService.createTimeSlotPricingRule(any(CreateTimeSlotPricingRuleRequest.class))).thenReturn(sampleRule);

        mockMvc.perform(post("/api/v1/admin/pricing/time-slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("ts-1"))
                .andExpect(jsonPath("$.name").value("18:00 - 22:00"));
    }

    @Test
    void updateTimeSlot_Success() throws Exception {
        UpdateTimeSlotPricingRuleRequest request = UpdateTimeSlotPricingRuleRequest.builder()
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(23, 0))
                .modifier(new BigDecimal("20000.00"))
                .build();

        TimeSlotPricingRuleResponse updated = TimeSlotPricingRuleResponse.builder()
                .id("ts-1")
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(23, 0))
                .modifier(new BigDecimal("20000.00"))
                .build();

        when(pricingService.updateTimeSlotPricingRule(eq("ts-1"), any(UpdateTimeSlotPricingRuleRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/admin/pricing/time-slots/ts-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("18:00 - 23:00"));
    }

    @Test
    void deleteTimeSlot_Success() throws Exception {
        doNothing().when(pricingService).deleteTimeSlotPricingRule("ts-1");

        mockMvc.perform(delete("/api/v1/admin/pricing/time-slots/ts-1"))
                .andExpect(status().isNoContent());
    }
}
