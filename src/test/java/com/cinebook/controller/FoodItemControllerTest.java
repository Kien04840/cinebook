package com.cinebook.controller;

import com.cinebook.dto.response.FoodItemResponse;
import com.cinebook.enums.FoodItemStatus;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.FoodItemService;
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
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FoodItemControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FoodItemService foodItemService;

    @InjectMocks
    private FoodItemController foodItemController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(foodItemController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/foods returns 200 with list of active foods")
    void getActiveFoods_Returns200() throws Exception {
        FoodItemResponse item = FoodItemResponse.builder()
                .id("food-1")
                .name("Bắp rang bơ phô mai (L)")
                .price(new BigDecimal("45000"))
                .status(FoodItemStatus.ACTIVE)
                .build();

        when(foodItemService.getActiveFoodItems()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/foods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("food-1"))
                .andExpect(jsonPath("$[0].name").value("Bắp rang bơ phô mai (L)"))
                .andExpect(jsonPath("$[0].price").value(45000));
    }
}

