package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowtimePricingPreviewResponse {
    private String showtimeId;
    private TicketPricingBreakdown baseBreakdown;
    private List<TicketPricingBreakdown> seatTypeBreakdowns;
}

