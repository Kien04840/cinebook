package com.cinebook.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu hoàn tiền cho giao dịch thanh toán")
public class RefundRequest {

    @Size(max = 255, message = "Lý do hoàn tiền không được vượt quá 255 ký tự.")
    @Schema(description = "Lý do hoàn tiền", example = "Khách hàng bận đột xuất, yêu cầu hủy vé và hoàn tiền.")
    private String reason;
}

