package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingStatisticsResponse {
    private LocalDateTime from;
    private LocalDateTime to;
    private Long totalBookings;
    private Long paidBookings;
    private Long cancelledBookings;
    private Long expiredBookings;
    private Long refundedBookings;
    private BigDecimal totalBookingAmount;
}

