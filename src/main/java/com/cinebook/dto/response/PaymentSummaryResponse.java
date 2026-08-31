package com.cinebook.dto.response;

import com.cinebook.enums.PaymentMethod;
import com.cinebook.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSummaryResponse {

    private String id;
    private PaymentMethod paymentMethod;
    private String paymentCode;
    private String gatewayTransactionId;
    private BigDecimal amount;
    private PaymentStatus paymentStatus;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}

