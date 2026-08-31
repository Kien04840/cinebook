package com.cinebook.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Phản hồi xác nhận cho VNPay IPN webhook")
public class IpnResponse {

    @JsonProperty("RspCode")
    @Schema(description = "Mã phản hồi kết quả IPN", example = "00")
    private String rspCode;

    @JsonProperty("Message")
    @Schema(description = "Thông báo mô tả kết quả xử lý IPN", example = "Confirm Success")
    private String message;
}

