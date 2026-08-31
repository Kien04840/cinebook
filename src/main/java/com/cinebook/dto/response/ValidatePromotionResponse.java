package com.cinebook.dto.response;

import com.cinebook.enums.PromotionDiscountType;
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
public class ValidatePromotionResponse {

    private boolean valid;
    private String code;
    private String name;
    private PromotionDiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal grossAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private String message;
}

