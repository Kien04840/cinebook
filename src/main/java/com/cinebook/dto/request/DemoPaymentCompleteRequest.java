package com.cinebook.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoPaymentCompleteRequest {

    @NotBlank(message = "Mã giao dịch thanh toán (paymentCode) không được để trống")
    private String paymentCode;

    @NotBlank(message = "Mã kết quả thanh toán (responseCode) không được để trống")
    @Pattern(
            regexp = "^(00|07|24)$",
            message = "Mã kết quả thanh toán không hợp lệ. Chỉ chấp nhận '00' (Thành công), '07' (Thất bại), '24' (Khách hàng hủy)"
    )
    private String responseCode;
}
