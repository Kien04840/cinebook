package com.cinebook.service.impl;

import com.cinebook.dto.request.CreateFoodItemRequest;
import com.cinebook.dto.request.UpdateFoodItemRequest;
import com.cinebook.dto.response.FoodItemResponse;
import com.cinebook.entity.FoodItem;
import com.cinebook.enums.FoodItemStatus;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.FoodItemMapper;
import com.cinebook.repository.FoodItemRepository;
import com.cinebook.service.FoodItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FoodItemServiceImpl implements FoodItemService {

    private final FoodItemRepository foodItemRepository;
    private final FoodItemMapper foodItemMapper;

    @Override
    public List<FoodItemResponse> getActiveFoodItems() {
        List<FoodItem> items = foodItemRepository.findByStatusAndDeletedAtIsNull(FoodItemStatus.ACTIVE);
        return foodItemMapper.toFoodItemResponseList(items);
    }

    @Override
    public List<FoodItemResponse> getAdminFoodItems(String keyword, FoodItemStatus status) {
        String trimmedKw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<FoodItem> items = foodItemRepository.searchAdminFoodItems(trimmedKw, status);
        return foodItemMapper.toFoodItemResponseList(items);
    }

    @Override
    public FoodItemResponse getFoodItemById(String id) {
        FoodItem item = foodItemRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn/thức uống với id: " + id));
        return foodItemMapper.toFoodItemResponse(item);
    }

    @Override
    @Transactional
    public FoodItemResponse createFoodItem(CreateFoodItemRequest request) {
        String trimmedName = request.getName().trim();
        if (foodItemRepository.existsByNameAndDeletedAtIsNull(trimmedName)) {
            throw new ConflictException("Món ăn/thức uống với tên '" + trimmedName + "' đã tồn tại trong hệ thống.");
        }

        FoodItem item = new FoodItem();
        item.setName(trimmedName);
        item.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        item.setPrice(request.getPrice());
        item.setImageUrl(StringUtils.hasText(request.getImageUrl()) ? request.getImageUrl().trim() : null);
        item.setStatus(request.getStatus() != null ? request.getStatus() : FoodItemStatus.ACTIVE);

        FoodItem saved = foodItemRepository.save(item);
        log.info("Tạo mới món ăn/thức uống thành công: id={}, name={}, price={}", saved.getId(), saved.getName(), saved.getPrice());
        return foodItemMapper.toFoodItemResponse(saved);
    }

    @Override
    @Transactional
    public FoodItemResponse updateFoodItem(String id, UpdateFoodItemRequest request) {
        FoodItem item = foodItemRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn/thức uống với id: " + id));

        String trimmedName = request.getName().trim();
        if (foodItemRepository.existsByNameAndIdNot(trimmedName, id)) {
            throw new ConflictException("Món ăn/thức uống với tên '" + trimmedName + "' đã tồn tại trong hệ thống.");
        }

        item.setName(trimmedName);
        item.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        item.setPrice(request.getPrice());
        item.setImageUrl(StringUtils.hasText(request.getImageUrl()) ? request.getImageUrl().trim() : null);
        item.setStatus(request.getStatus());

        FoodItem updated = foodItemRepository.save(item);
        log.info("Cập nhật món ăn/thức uống thành công: id={}, name={}, price={}, status={}", updated.getId(), updated.getName(), updated.getPrice(), updated.getStatus());
        return foodItemMapper.toFoodItemResponse(updated);
    }

    @Override
    @Transactional
    public void deleteFoodItem(String id) {
        FoodItem item = foodItemRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn/thức uống với id: " + id));

        item.setDeletedAt(LocalDateTime.now());
        item.setStatus(FoodItemStatus.INACTIVE);
        foodItemRepository.save(item);
        log.info("Xóa mềm món ăn/thức uống thành công: id={}, name={}", item.getId(), item.getName());
    }
}

