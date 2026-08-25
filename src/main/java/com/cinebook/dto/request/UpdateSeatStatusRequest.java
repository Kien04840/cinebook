package com.cinebook.dto.request;

import com.cinebook.enums.SeatStatus;
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
public class UpdateSeatStatusRequest {

    @NotNull(message = "Seat status is required")
    private SeatStatus status;
}