package com.cinebook.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTimeSlotPricingRuleRequest {

    @NotNull(message = "Thời gian bắt đầu không được để trống")
    @JsonFormat(pattern = "HH:mm[:ss]")
    private LocalTime startTime;

    @NotNull(message = "Thời gian kết thúc không được để trống")
    @JsonFormat(pattern = "HH:mm[:ss]")
    private LocalTime endTime;

    @NotNull(message = "Giá trị điều chỉnh (modifier) không được để trống")
    private BigDecimal modifier;

    private String name;
}

