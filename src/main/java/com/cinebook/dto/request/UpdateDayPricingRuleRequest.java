package com.cinebook.dto.request;

import jakarta.validation.constraints.NotNull;
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
public class UpdateDayPricingRuleRequest {

    @NotNull(message = "Giá trị điều chỉnh (modifier) không được để trống")
    private BigDecimal modifier;
}

