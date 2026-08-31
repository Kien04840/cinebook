package com.cinebook.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Thông tin phản hồi khi khởi tạo phiên thanh toán VNPay")
public class InitiatePaymentResponse {

    @Schema(description = "ID của bản ghi thanh toán", example = "550e8400-e29b-41d4-a716-446655440000")
    private String paymentId;

    @Schema(description = "Mã thanh toán duy nhất (vnp_TxnRef)", example = "PAY-20260901-7F8A2B1C")
    private String paymentCode;

    @Schema(description = "Số tiền thanh toán", example = "240000.00")
    private BigDecimal amount;

    @Schema(description = "Đường dẫn chuyển hướng thanh toán VNPay Sandbox")
    private String paymentUrl;

    @Schema(description = "Thời gian hết hạn giữ chỗ của đơn hàng", example = "2026-09-01T10:05:00")
    private LocalDateTime expiresAt;
}

