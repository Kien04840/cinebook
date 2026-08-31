package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingKpiResponse {
    private Long totalBookings;
    private Long paidBookings;
    private Long cancelledBookings;
    private Long expiredBookings;
    private Long refundedBookings;
}

