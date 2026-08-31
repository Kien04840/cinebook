package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketKpiResponse {
    private Long grossTicketsSold;
    private Long refundedTickets;
    private Long netTicketsSold;
}

