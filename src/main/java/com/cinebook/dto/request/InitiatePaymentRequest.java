package com.cinebook.dto.request;

import com.cinebook.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Yêu cầu tạo phiên thanh toán cho đơn đặt vé")
public class InitiatePaymentRequest {

    @NotNull(message = "Phương thức thanh toán không được để trống")
    @Schema(description = "Phương thức thanh toán (V1 hỗ trợ VNPAY)", example = "VNPAY")
    private PaymentMethod paymentMethod;
}

