package com.cinebook.dto.response;

import com.cinebook.enums.FoodItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodItemResponse {

    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private FoodItemStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

