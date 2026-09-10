package com.cinebook.service;

import com.cinebook.dto.request.CreateFoodItemRequest;
import com.cinebook.dto.request.UpdateFoodItemRequest;
import com.cinebook.dto.response.FoodItemResponse;
import com.cinebook.enums.FoodItemStatus;

import java.util.List;

public interface FoodItemService {

    /**
     * Lấy danh sách toàn bộ món ăn / đồ uống đang hoạt động (cho khách hàng)
     */
    List<FoodItemResponse> getActiveFoodItems();

    /**
     * Lấy danh sách món ăn / đồ uống cho quản trị viên với bộ lọc tìm kiếm
     */
    List<FoodItemResponse> getAdminFoodItems(String keyword, FoodItemStatus status);

    /**
     * Lấy chi tiết món ăn / đồ uống theo ID
     */
    FoodItemResponse getFoodItemById(String id);

    /**
     * Tạo mới món ăn / đồ uống (Admin)
     */
    FoodItemResponse createFoodItem(CreateFoodItemRequest request);

    /**
     * Cập nhật món ăn / đồ uống (Admin)
     */
    FoodItemResponse updateFoodItem(String id, UpdateFoodItemRequest request);

    /**
     * Xóa mềm món ăn / đồ uống (Admin)
     */
    void deleteFoodItem(String id);
}

