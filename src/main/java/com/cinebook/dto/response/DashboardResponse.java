package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private LocalDateTime from;
    private LocalDateTime to;
    private FinancialKpiResponse financial;
    private TicketKpiResponse tickets;
    private BookingKpiResponse bookings;
    private UserKpiResponse users;
    private OperationKpiResponse operations;
}

