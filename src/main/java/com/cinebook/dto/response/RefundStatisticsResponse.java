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
public class RefundStatisticsResponse {
    private LocalDateTime from;
    private LocalDateTime to;
    private Long totalRefunds;
    private Long successfulRefunds;
    private Long failedRefunds;
    private Long pendingRefunds;
    private BigDecimal totalRefundAmount;
}

