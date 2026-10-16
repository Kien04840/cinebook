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
    private BigDecimal totalRevenue;
    private Long grossTicketsSold;
    private Long refundedTickets;
    private Long netTicketsSold;
    private Long ticketsSold;
    private Long totalShowtimes;

    public BigDecimal getTotalRevenue() {
        if (totalRevenue != null) return totalRevenue;
        return netRevenue != null ? netRevenue : (grossRevenue != null ? grossRevenue : BigDecimal.ZERO);
    }

    public Long getTicketsSold() {
        if (ticketsSold != null) return ticketsSold;
        return netTicketsSold != null ? netTicketsSold : (grossTicketsSold != null ? grossTicketsSold : 0L);
    }
}

