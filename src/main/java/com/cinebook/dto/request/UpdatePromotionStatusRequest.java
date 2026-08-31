package com.cinebook.dto.request;

import com.cinebook.enums.PromotionStatus;
import jakarta.validation.constraints.NotNull;
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
public class UpdatePromotionStatusRequest {

    @NotNull(message = "Promotion status is required")
    private PromotionStatus status;
}

