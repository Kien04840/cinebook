package com.cinebook.dto.request;

import com.cinebook.enums.SeatStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchUpdateSeatStatusRequest {

    @NotEmpty(message = "Seat IDs list cannot be empty")
    private List<String> seatIds;

    @NotNull(message = "Seat status is required")
    private SeatStatus status;
}
