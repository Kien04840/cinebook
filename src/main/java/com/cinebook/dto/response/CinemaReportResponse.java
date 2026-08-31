package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CinemaReportResponse {
    private Integer rank;
    private String cinemaId;
    private String cinemaName;
    private String city;
    private BigDecimal grossRevenue;
    private BigDecimal refundAmount;
    private BigDecimal netRevenue;
    private Long grossTicketsSold;
    private Long refundedTickets;
    private Long netTicketsSold;
}

