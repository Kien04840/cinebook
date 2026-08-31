package com.cinebook.controller;

import com.cinebook.dto.request.CreatePromotionRequest;
import com.cinebook.dto.request.UpdatePromotionRequest;
import com.cinebook.dto.request.UpdatePromotionStatusRequest;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.PromotionResponse;
import com.cinebook.enums.PromotionDiscountType;
import com.cinebook.enums.PromotionStatus;
import com.cinebook.service.PromotionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminPromotionControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private PromotionService promotionService;

    @InjectMocks
    private AdminPromotionController adminPromotionController;

    private PromotionResponse testResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminPromotionController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        testResponse = PromotionResponse.builder()
                .id("promo-1")
                .code("SUMMER20")
                .name("Giảm 20% mùa hè")
                .description("Áp dụng toàn quốc")
                .discountType(PromotionDiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20.00"))
                .minOrderAmount(new BigDecimal("100000.00"))
                .maxDiscountAmount(new BigDecimal("50000.00"))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .usageLimit(100)
                .usedCount(5)
                .remainingUses(95)
                .status(PromotionStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/admin/promotions - List promotions with pagination -> 200 OK")
    void testGetAdminPromotions() throws Exception {
        PageResponse<PromotionResponse> pageResponse = PageResponse.of(new PageImpl<>(List.of(testResponse), PageRequest.of(0, 20), 1));
        when(promotionService.getAdminPromotions(any(), any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/promotions")
                        .param("q", "SUMMER")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("SUMMER20"))
                .andExpect(jsonPath("$.content[0].remainingUses").value(95));
    }

    @Test
    @DisplayName("GET /api/v1/admin/promotions/{id} - Detail -> 200 OK")
    void testGetPromotionDetail() throws Exception {
        when(promotionService.getPromotionDetail("promo-1")).thenReturn(testResponse);

        mockMvc.perform(get("/api/v1/admin/promotions/promo-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("promo-1"))
                .andExpect(jsonPath("$.code").value("SUMMER20"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/promotions - Create promotion -> 201 Created")
    void testCreatePromotion() throws Exception {
        CreatePromotionRequest request = CreatePromotionRequest.builder()
                .code("SUMMER20")
                .name("Giảm 20% mùa hè")
                .discountType(PromotionDiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20.00"))
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(5))
                .build();

        when(promotionService.createPromotion(any(CreatePromotionRequest.class))).thenReturn(testResponse);

        mockMvc.perform(post("/api/v1/admin/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUMMER20"));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/promotions/{id} - Update promotion -> 200 OK")
    void testUpdatePromotion() throws Exception {
        UpdatePromotionRequest request = UpdatePromotionRequest.builder()
                .name("Updated Summer")
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(10))
                .build();

        when(promotionService.updatePromotion(eq("promo-1"), any(UpdatePromotionRequest.class))).thenReturn(testResponse);

        mockMvc.perform(put("/api/v1/admin/promotions/promo-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/promotions/{id}/status - Toggle status -> 200 OK")
    void testUpdatePromotionStatus() throws Exception {
        UpdatePromotionStatusRequest request = new UpdatePromotionStatusRequest(PromotionStatus.INACTIVE);
        when(promotionService.updatePromotionStatus(eq("promo-1"), any(UpdatePromotionStatusRequest.class))).thenReturn(testResponse);

        mockMvc.perform(patch("/api/v1/admin/promotions/promo-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}

