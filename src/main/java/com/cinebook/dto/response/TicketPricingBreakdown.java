package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.DayOfWeek;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketPricingBreakdown {
    private BigDecimal basePrice;
    private BigDecimal seatTypeModifier;
    private BigDecimal dayModifier;
    private DayOfWeek dayOfWeek;
    private BigDecimal timeSlotModifier;
    private String timeSlotRange;
    private BigDecimal finalPrice;
}

