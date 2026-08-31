package com.cinebook.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class UpdatePromotionRequest {

    @NotBlank(message = "Promotion name is required")
    @Size(max = 255, message = "Promotion name must not exceed 255 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @DecimalMin(value = "0.00", message = "Minimum order amount must be non-negative")
    private BigDecimal minOrderAmount;

    @DecimalMin(value = "0.01", message = "Maximum discount amount must be greater than 0")
    private BigDecimal maxDiscountAmount;

    @NotNull(message = "Start date is required")
    private LocalDateTime startAt;

    @NotNull(message = "End date is required")
    private LocalDateTime endAt;

    @Min(value = 1, message = "Usage limit must be at least 1")
    private Integer usageLimit;
}

