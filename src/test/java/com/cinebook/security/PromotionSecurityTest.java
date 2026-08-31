package com.cinebook.security;

import com.cinebook.dto.request.CreatePromotionRequest;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.PromotionResponse;
import com.cinebook.dto.response.ValidatePromotionResponse;
import com.cinebook.enums.PromotionDiscountType;
import com.cinebook.enums.PromotionStatus;
import com.cinebook.service.PromotionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PromotionSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private PromotionService promotionService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Security: Anonymous user cannot access admin promotions list -> 401 Unauthorized")
    void anonymous_ListAdminPromotions_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/promotions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Security: Anonymous user cannot create promotion -> 401 Unauthorized")
    void anonymous_CreatePromotion_Returns401() throws Exception {
        CreatePromotionRequest request = CreatePromotionRequest.builder()
                .code("SUMMER20")
                .name("Summer 20%")
                .discountType(PromotionDiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20.00"))
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(5))
                .build();

        mockMvc.perform(post("/api/v1/admin/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("Security: Customer role cannot access admin promotions list -> 403 Forbidden")
    void customer_ListAdminPromotions_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/promotions"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("Security: Customer role cannot create promotion -> 403 Forbidden")
    void customer_CreatePromotion_Returns403() throws Exception {
        CreatePromotionRequest request = CreatePromotionRequest.builder()
                .code("SUMMER20")
                .name("Summer 20%")
                .discountType(PromotionDiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20.00"))
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(5))
                .build();

        mockMvc.perform(post("/api/v1/admin/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Security: Admin role can list admin promotions -> 200 OK")
    void admin_ListAdminPromotions_Allowed() throws Exception {
        when(promotionService.getAdminPromotions(any(), any(), any()))
                .thenReturn(PageResponse.of(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)));

        mockMvc.perform(get("/api/v1/admin/promotions"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Security: Admin role can create promotion -> 201 Created")
    void admin_CreatePromotion_Allowed() throws Exception {
        CreatePromotionRequest request = CreatePromotionRequest.builder()
                .code("SUMMER20")
                .name("Summer 20%")
                .discountType(PromotionDiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20.00"))
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(5))
                .build();

        when(promotionService.createPromotion(any(CreatePromotionRequest.class)))
                .thenReturn(PromotionResponse.builder().id("p1").code("SUMMER20").build());

        mockMvc.perform(post("/api/v1/admin/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Security: Anonymous user can access public validate preview -> 200 OK")
    void anonymous_ValidatePromotion_Allowed() throws Exception {
        ValidatePromotionResponse response = ValidatePromotionResponse.builder()
                .valid(true)
                .code("SUMMER20")
                .grossAmount(new BigDecimal("200000.00"))
                .discountAmount(new BigDecimal("40000.00"))
                .finalAmount(new BigDecimal("160000.00"))
                .message("Áp dụng mã giảm giá thành công.")
                .build();

        when(promotionService.validatePromotionCode(any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/promotions/validate")
                        .param("code", "SUMMER20")
                        .param("grossAmount", "200000.00"))
                .andExpect(status().isOk());
    }
}

