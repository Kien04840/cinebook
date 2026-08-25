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

    @NotBlank(message = "Seat type name is required")
    @Size(max = 100, message = "Seat type name cannot exceed 100 characters")
    private String name;

    @NotNull(message = "Price modifier is required")
    @DecimalMin(value = "0.0", message = "Price modifier cannot be negative")
    private BigDecimal priceModifier;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    private SeatTypeStatus status;
}