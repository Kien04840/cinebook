package com.cinebook.controller;

import com.cinebook.dto.request.CreateFoodItemRequest;
import com.cinebook.dto.request.UpdateFoodItemRequest;
import com.cinebook.dto.response.FoodItemResponse;
import com.cinebook.enums.FoodItemStatus;
import com.cinebook.service.FoodItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/foods")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Food & Concessions", description = "API quản trị danh mục bắp nước, combo đồ ăn thức uống rạp chiếu phim (ADMIN)")
public class AdminFoodItemController {

    private final FoodItemService foodItemService;

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả món ăn/thức uống cho quản trị viên", description = "Tìm kiếm theo từ khóa và trạng thái")
    public ResponseEntity<List<FoodItemResponse>> getAllFoodItems(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) FoodItemStatus status
    ) {
        return ResponseEntity.ok(foodItemService.getAdminFoodItems(q, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết món ăn/thức uống theo ID")
    public ResponseEntity<FoodItemResponse> getFoodItemById(@PathVariable String id) {
        return ResponseEntity.ok(foodItemService.getFoodItemById(id));
    }

    @PostMapping
    @Operation(summary = "Tạo mới món ăn/thức uống")
    public ResponseEntity<FoodItemResponse> createFoodItem(@Valid @RequestBody CreateFoodItemRequest request) {
        FoodItemResponse created = foodItemService.createFoodItem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin món ăn/thức uống")
    public ResponseEntity<FoodItemResponse> updateFoodItem(
            @PathVariable String id,
            @Valid @RequestBody UpdateFoodItemRequest request
    ) {
        return ResponseEntity.ok(foodItemService.updateFoodItem(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa (mềm) món ăn/thức uống")
    public ResponseEntity<Void> deleteFoodItem(@PathVariable String id) {
        foodItemService.deleteFoodItem(id);
        return ResponseEntity.noContent().build();
    }
}

