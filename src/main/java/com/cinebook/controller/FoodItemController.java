package com.cinebook.controller;

import com.cinebook.dto.response.FoodItemResponse;
import com.cinebook.service.FoodItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/foods")
@RequiredArgsConstructor
@Tag(name = "Food & Concessions", description = "API tra cứu danh mục bắp nước, combo đồ ăn thức uống rạp chiếu phim")
public class FoodItemController {

    private final FoodItemService foodItemService;

    @GetMapping
    @Operation(summary = "Lấy danh sách bắp nước đang phục vụ", description = "Trả về toàn bộ danh mục bắp nước, combo đang ở trạng thái ACTIVE để khách hàng chọn khi đặt vé")
    public ResponseEntity<List<FoodItemResponse>> getActiveFoodItems() {
        return ResponseEntity.ok(foodItemService.getActiveFoodItems());
    }
}

