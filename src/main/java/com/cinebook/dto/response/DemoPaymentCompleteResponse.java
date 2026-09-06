package com.cinebook.dto.response;

import com.cinebook.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoPaymentCompleteResponse {

    private String paymentCode;
    private String responseCode;
    private PaymentStatus paymentStatus;
    private String redirectUrl;
    private String message;
}
