package com.cinebook.dto.request;

import com.cinebook.enums.FoodItemStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFoodItemRequest {

    @NotBlank(message = "Tên món ăn/thức uống không được để trống")
    @Size(max = 100, message = "Tên món không được vượt quá 100 ký tự")
    private String name;

    @Size(max = 255, message = "Mô tả không được vượt quá 255 ký tự")
    private String description;

    @NotNull(message = "Giá tiền không được để trống")
    @DecimalMin(value = "1000.00", message = "Giá tiền tối thiểu là 1,000 VND")
    private BigDecimal price;

    @Size(max = 500, message = "URL hình ảnh không được vượt quá 500 ký tự")
    private String imageUrl;

    @NotNull(message = "Trạng thái không được để trống")
    private FoodItemStatus status;
}

