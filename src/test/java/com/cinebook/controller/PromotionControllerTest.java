package com.cinebook.controller;

import com.cinebook.dto.response.ValidatePromotionResponse;
import com.cinebook.enums.PromotionDiscountType;
import com.cinebook.service.PromotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PromotionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PromotionService promotionService;

    @InjectMocks
    private PromotionController promotionController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(promotionController).build();
    }

    @Test
    @DisplayName("GET /api/v1/promotions/validate - Valid promotion code preview -> 200 OK")
    void testValidatePromotion_Valid() throws Exception {
        ValidatePromotionResponse response = ValidatePromotionResponse.builder()
                .valid(true)
                .code("SUMMER20")
                .name("Giảm 20% mùa hè")
                .discountType(PromotionDiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20.00"))
                .grossAmount(new BigDecimal("200000.00"))
                .discountAmount(new BigDecimal("40000.00"))
                .finalAmount(new BigDecimal("160000.00"))
                .message("Áp dụng mã giảm giá thành công.")
                .build();

        when(promotionService.validatePromotionCode(eq("SUMMER20"), any(BigDecimal.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/promotions/validate")
                        .param("code", "SUMMER20")
                        .param("grossAmount", "200000.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.code").value("SUMMER20"))
                .andExpect(jsonPath("$.discountAmount").value(40000.00))
                .andExpect(jsonPath("$.finalAmount").value(160000.00));
    }

    @Test
    @DisplayName("GET /api/v1/promotions/validate - Invalid/Expired code preview -> 200 OK with valid=false")
    void testValidatePromotion_Invalid() throws Exception {
        ValidatePromotionResponse response = ValidatePromotionResponse.builder()
                .valid(false)
                .code("EXPIRED")
                .grossAmount(new BigDecimal("200000.00"))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("200000.00"))
                .message("Mã giảm giá đã hết hạn sử dụng.")
                .build();

        when(promotionService.validatePromotionCode(eq("EXPIRED"), any(BigDecimal.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/promotions/validate")
                        .param("code", "EXPIRED")
                        .param("grossAmount", "200000.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("Mã giảm giá đã hết hạn sử dụng."));
    }
}

