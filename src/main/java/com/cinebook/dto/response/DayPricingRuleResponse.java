package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DayPricingRuleResponse {
    private String id;
    private DayOfWeek dayOfWeek;
    private BigDecimal modifier;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

