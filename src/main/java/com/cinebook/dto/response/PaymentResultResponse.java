package com.cinebook.dto.response;

import com.cinebook.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Kết quả thanh toán trả về cho giao diện người dùng sau khi redirect từ VNPay")
public class PaymentResultResponse {

    @Schema(description = "ID của bản ghi thanh toán", example = "550e8400-e29b-41d4-a716-446655440000")
    private String paymentId;

    @Schema(description = "ID của đơn đặt vé", example = "c4b1e840-7988-4c6e-a2f1-9d2123456789")
    private String bookingId;

    @Schema(description = "Mã đơn đặt vé", example = "CB-20260901-8F32A1")
    private String bookingCode;

    @Schema(description = "Mã thanh toán (vnp_TxnRef)", example = "PAY-20260901-7F8A2B1C")
    private String paymentCode;

    @Schema(description = "Số tiền thanh toán", example = "240000.00")
    private BigDecimal amount;

    @Schema(description = "Trạng thái thanh toán hiện tại trong hệ thống", example = "SUCCESS")
    private PaymentStatus paymentStatus;

    @Schema(description = "Mã phản hồi từ cổng VNPay", example = "00")
    private String responseCode;

    @Schema(description = "Thông báo kết quả cho khách hàng", example = "Giao dịch thành công.")
    private String message;
}

