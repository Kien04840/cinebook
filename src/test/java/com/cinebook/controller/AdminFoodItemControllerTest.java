package com.cinebook.controller;

import com.cinebook.dto.request.CreateFoodItemRequest;
import com.cinebook.dto.request.UpdateFoodItemRequest;
import com.cinebook.dto.response.FoodItemResponse;
import com.cinebook.enums.FoodItemStatus;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.FoodItemService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminFoodItemControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private FoodItemService foodItemService;

    @InjectMocks
    private AdminFoodItemController adminFoodItemController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminFoodItemController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/admin/foods returns 200 with list of all foods")
    void getAdminFoods_Returns200() throws Exception {
        FoodItemResponse item = FoodItemResponse.builder()
                .id("food-1")
                .name("Combo Solo")
                .price(new BigDecimal("69000"))
                .status(FoodItemStatus.ACTIVE)
                .build();

        when(foodItemService.getAdminFoodItems(null, null)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/admin/foods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("food-1"))
                .andExpect(jsonPath("$[0].name").value("Combo Solo"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/foods creates food item and returns 201")
    void createFood_Returns201() throws Exception {
        CreateFoodItemRequest request = CreateFoodItemRequest.builder()
                .name("Combo Family")
                .price(new BigDecimal("159000"))
                .status(FoodItemStatus.ACTIVE)
                .build();

        FoodItemResponse response = FoodItemResponse.builder()
                .id("food-family")
                .name("Combo Family")
                .price(new BigDecimal("159000"))
                .status(FoodItemStatus.ACTIVE)
                .build();

        when(foodItemService.createFoodItem(any(CreateFoodItemRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/foods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("food-family"))
                .andExpect(jsonPath("$.name").value("Combo Family"))
                .andExpect(jsonPath("$.price").value(159000));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/foods/{id} updates food item and returns 200")
    void updateFood_Returns200() throws Exception {
        UpdateFoodItemRequest request = UpdateFoodItemRequest.builder()
                .name("Combo Family VIP")
                .price(new BigDecimal("179000"))
                .status(FoodItemStatus.ACTIVE)
                .build();

        FoodItemResponse response = FoodItemResponse.builder()
                .id("food-family")
                .name("Combo Family VIP")
                .price(new BigDecimal("179000"))
                .status(FoodItemStatus.ACTIVE)
                .build();

        when(foodItemService.updateFoodItem(eq("food-family"), any(UpdateFoodItemRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/foods/food-family")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Combo Family VIP"))
                .andExpect(jsonPath("$.price").value(179000));
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/foods/{id} deletes food item and returns 204")
    void deleteFood_Returns204() throws Exception {
        doNothing().when(foodItemService).deleteFoodItem("food-1");

        mockMvc.perform(delete("/api/v1/admin/foods/food-1"))
                .andExpect(status().isNoContent());
    }
}
