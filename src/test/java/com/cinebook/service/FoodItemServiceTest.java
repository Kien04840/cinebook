package com.cinebook.service;

import com.cinebook.dto.request.CreateFoodItemRequest;
import com.cinebook.dto.request.UpdateFoodItemRequest;
import com.cinebook.dto.response.FoodItemResponse;
import com.cinebook.entity.FoodItem;
import com.cinebook.enums.FoodItemStatus;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.FoodItemMapper;
import com.cinebook.repository.FoodItemRepository;
import com.cinebook.service.impl.FoodItemServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FoodItemServiceTest {

    @Mock
    private FoodItemRepository foodItemRepository;

    @Spy
    private FoodItemMapper foodItemMapper = new FoodItemMapper();

    @InjectMocks
    private FoodItemServiceImpl foodItemService;

    private FoodItem sampleFood;

    @BeforeEach
    void setUp() {
        sampleFood = new FoodItem();
        sampleFood.setId("food-1");
        sampleFood.setName("Bắp rang bơ phô mai");
        sampleFood.setDescription("Bắp rang phô mai cỡ lớn");
        sampleFood.setPrice(new BigDecimal("45000"));
        sampleFood.setImageUrl("https://example.com/popcorn.jpg");
        sampleFood.setStatus(FoodItemStatus.ACTIVE);
    }

    @Test
    @DisplayName("getActiveFoodItems should return only active and not deleted foods")
    void getActiveFoodItems_Success() {
        when(foodItemRepository.findByStatusAndDeletedAtIsNull(FoodItemStatus.ACTIVE))
                .thenReturn(List.of(sampleFood));

        List<FoodItemResponse> result = foodItemService.getActiveFoodItems();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Bắp rang bơ phô mai", result.get(0).getName());
        assertEquals(new BigDecimal("45000"), result.get(0).getPrice());
        verify(foodItemRepository).findByStatusAndDeletedAtIsNull(FoodItemStatus.ACTIVE);
    }

    @Test
    @DisplayName("getAdminFoodItems should filter foods correctly")
    void getAdminFoodItems_Success() {
        when(foodItemRepository.searchAdminFoodItems("bắp", FoodItemStatus.ACTIVE))
                .thenReturn(List.of(sampleFood));

        List<FoodItemResponse> result = foodItemService.getAdminFoodItems("bắp", FoodItemStatus.ACTIVE);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(foodItemRepository).searchAdminFoodItems("bắp", FoodItemStatus.ACTIVE);
    }

    @Test
    @DisplayName("createFoodItem should save new food when name is unique")
    void createFoodItem_Success() {
        CreateFoodItemRequest request = CreateFoodItemRequest.builder()
                .name("Nước ngọt Coca-Cola")
                .description("Ly lớn")
                .price(new BigDecimal("28000"))
                .status(FoodItemStatus.ACTIVE)
                .build();

        when(foodItemRepository.existsByNameAndDeletedAtIsNull("Nước ngọt Coca-Cola"))
                .thenReturn(false);
        when(foodItemRepository.save(any(FoodItem.class))).thenAnswer(inv -> {
            FoodItem f = inv.getArgument(0);
            f.setId("food-2");
            return f;
        });

        FoodItemResponse response = foodItemService.createFoodItem(request);

        assertNotNull(response);
        assertEquals("food-2", response.getId());
        assertEquals("Nước ngọt Coca-Cola", response.getName());
        assertEquals(new BigDecimal("28000"), response.getPrice());
        verify(foodItemRepository).save(any(FoodItem.class));
    }

    @Test
    @DisplayName("createFoodItem should throw ConflictException when name already exists")
    void createFoodItem_DuplicateName_ThrowsConflict() {
        CreateFoodItemRequest request = CreateFoodItemRequest.builder()
                .name("Bắp rang bơ phô mai")
                .price(new BigDecimal("45000"))
                .build();

        when(foodItemRepository.existsByNameAndDeletedAtIsNull("Bắp rang bơ phô mai"))
                .thenReturn(true);

        assertThrows(ConflictException.class, () -> foodItemService.createFoodItem(request));
        verify(foodItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateFoodItem should update fields successfully")
    void updateFoodItem_Success() {
        UpdateFoodItemRequest request = UpdateFoodItemRequest.builder()
                .name("Bắp phô mai đặc biệt")
                .price(new BigDecimal("50000"))
                .status(FoodItemStatus.INACTIVE)
                .build();

        when(foodItemRepository.findByIdAndDeletedAtIsNull("food-1")).thenReturn(Optional.of(sampleFood));
        when(foodItemRepository.existsByNameAndIdNot("Bắp phô mai đặc biệt", "food-1"))
                .thenReturn(false);
        when(foodItemRepository.save(any(FoodItem.class))).thenAnswer(inv -> inv.getArgument(0));

        FoodItemResponse response = foodItemService.updateFoodItem("food-1", request);

        assertNotNull(response);
        assertEquals("Bắp phô mai đặc biệt", response.getName());
        assertEquals(new BigDecimal("50000"), response.getPrice());
        assertEquals(FoodItemStatus.INACTIVE, response.getStatus());
        verify(foodItemRepository).save(sampleFood);
    }

    @Test
    @DisplayName("updateFoodItem should throw ConflictException if updated name conflicts with another item")
    void updateFoodItem_DuplicateName_ThrowsConflict() {
        UpdateFoodItemRequest request = UpdateFoodItemRequest.builder()
                .name("Bắp rang bơ caramel")
                .build();

        when(foodItemRepository.findByIdAndDeletedAtIsNull("food-1")).thenReturn(Optional.of(sampleFood));
        when(foodItemRepository.existsByNameAndIdNot("Bắp rang bơ caramel", "food-1"))
                .thenReturn(true);

        assertThrows(ConflictException.class, () -> foodItemService.updateFoodItem("food-1", request));
        verify(foodItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateFoodItem should throw ResourceNotFoundException if item not found")
    void updateFoodItem_NotFound_ThrowsException() {
        UpdateFoodItemRequest request = UpdateFoodItemRequest.builder().name("Test").build();
        when(foodItemRepository.findByIdAndDeletedAtIsNull("food-missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> foodItemService.updateFoodItem("food-missing", request));
    }

    @Test
    @DisplayName("deleteFoodItem should soft delete food item")
    void deleteFoodItem_Success() {
        when(foodItemRepository.findByIdAndDeletedAtIsNull("food-1")).thenReturn(Optional.of(sampleFood));
        when(foodItemRepository.save(any(FoodItem.class))).thenAnswer(inv -> inv.getArgument(0));

        foodItemService.deleteFoodItem("food-1");

        assertNotNull(sampleFood.getDeletedAt());
        assertEquals(FoodItemStatus.INACTIVE, sampleFood.getStatus());
        verify(foodItemRepository).save(sampleFood);
    }

    @Test
    @DisplayName("deleteFoodItem should throw ResourceNotFoundException if item not found")
    void deleteFoodItem_NotFound_ThrowsException() {
        when(foodItemRepository.findByIdAndDeletedAtIsNull("food-missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> foodItemService.deleteFoodItem("food-missing"));
    }
}

