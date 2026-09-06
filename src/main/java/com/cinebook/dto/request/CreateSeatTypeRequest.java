package com.cinebook.dto.request;

import com.cinebook.enums.SeatTypeStatus;
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
public class CreateSeatTypeRequest {

    @NotBlank(message = "Seat type code is required")
    @Size(max = 50, message = "Seat type code cannot exceed 50 characters")
    @jakarta.validation.constraints.Pattern(regexp = "^[A-Z0-9_]+$", message = "Seat type code must contain only uppercase letters, numbers and underscores")
    private String code;

    @NotBlank(message = "Seat type name is required")
    @Size(max = 100, message = "Seat type name cannot exceed 100 characters")
    private String name;

    @NotNull(message = "Price modifier is required")
    @DecimalMin(value = "0.0", message = "Price modifier cannot be negative")
    private BigDecimal priceModifier;

    @NotNull(message = "Capacity is required")
    @jakarta.validation.constraints.Min(value = 1, message = "Capacity must be at least 1")
    @jakarta.validation.constraints.Max(value = 4, message = "Capacity cannot exceed 4")
    private Short capacity;

    @Size(max = 30, message = "Color token cannot exceed 30 characters")
    private String colorToken;

    @Size(max = 50, message = "Icon cannot exceed 50 characters")
    private String icon;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    private SeatTypeStatus status;
}