package com.cinebook.dto.response;

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
public class BookingFoodResponse {

    private String id;
    private String foodItemId;
    private String foodName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal subtotal;
}

