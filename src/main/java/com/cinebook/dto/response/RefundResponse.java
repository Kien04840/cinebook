package com.cinebook.dto.response;

import com.cinebook.enums.RefundStatus;
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết kết quả hoàn tiền")
public class RefundResponse {

    @Schema(description = "ID bản ghi hoàn tiền")
    private String id;

    @Schema(description = "ID giao dịch thanh toán gốc")
    private String paymentId;

    @Schema(description = "ID đơn đặt vé")
    private String bookingId;

    @Schema(description = "Mã đơn đặt vé")
    private String bookingCode;

    @Schema(description = "Mã hoàn tiền hệ thống")
    private String refundCode;

    @Schema(description = "Mã giao dịch hoàn tiền từ cổng thanh toán")
    private String gatewayRefundId;

    @Schema(description = "Số tiền hoàn lại")
    private BigDecimal amount;

    @Schema(description = "Lý do hoàn tiền")
    private String refundReason;

    @Schema(description = "Trạng thái hoàn tiền (PENDING, SUCCESS, FAILED)")
    private RefundStatus refundStatus;

    @Schema(description = "Thời điểm hoàn tiền hoàn tất")
    private LocalDateTime processedAt;

    @Schema(description = "Thời điểm tạo yêu cầu hoàn tiền")
    private LocalDateTime createdAt;
}

