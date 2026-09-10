package com.cinebook.mapper;

import com.cinebook.dto.response.BookingFoodResponse;
import com.cinebook.dto.response.FoodItemResponse;
import com.cinebook.entity.BookingFood;
import com.cinebook.entity.FoodItem;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class FoodItemMapper {

    public FoodItemResponse toFoodItemResponse(FoodItem item) {
        if (item == null) {
            return null;
        }

        return FoodItemResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .description(item.getDescription())
                .price(item.getPrice())
                .imageUrl(item.getImageUrl())
                .status(item.getStatus())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    public List<FoodItemResponse> toFoodItemResponseList(List<FoodItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream()
                .map(this::toFoodItemResponse)
                .toList();
    }

    public BookingFoodResponse toBookingFoodResponse(BookingFood bf) {
        if (bf == null) {
            return null;
        }

        return BookingFoodResponse.builder()
                .id(bf.getId())
                .foodItemId(bf.getFoodItem() != null ? bf.getFoodItem().getId() : null)
                .foodName(bf.getFoodName())
                .unitPrice(bf.getUnitPrice())
                .quantity(bf.getQuantity())
                .subtotal(bf.getSubtotal())
                .build();
    }

    public List<BookingFoodResponse> toBookingFoodResponseList(List<BookingFood> foods) {
        if (foods == null || foods.isEmpty()) {
            return Collections.emptyList();
        }
        return foods.stream()
                .map(this::toBookingFoodResponse)
                .toList();
    }
}

