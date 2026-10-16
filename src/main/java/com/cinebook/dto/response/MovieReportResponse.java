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
public class MovieReportResponse {
    private Integer rank;
    private String movieId;
    private String movieTitle;
    private String posterUrl;
    private BigDecimal grossRevenue;
    private BigDecimal refundAmount;
    private BigDecimal netRevenue;
    private BigDecimal totalRevenue;
    private Long grossTicketsSold;
    private Long refundedTickets;
    private Long netTicketsSold;
    private Long ticketsSold;
    private Long totalBookings;

    public BigDecimal getTotalRevenue() {
        if (totalRevenue != null) return totalRevenue;
        return netRevenue != null ? netRevenue : (grossRevenue != null ? grossRevenue : BigDecimal.ZERO);
    }

    public Long getTicketsSold() {
        if (ticketsSold != null) return ticketsSold;
        return netTicketsSold != null ? netTicketsSold : (grossTicketsSold != null ? grossTicketsSold : 0L);
    }
}

