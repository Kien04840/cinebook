package com.cinebook.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
public class CreateBookingRequest {

    @NotBlank(message = "Showtime ID is required")
    private String showtimeId;

    @NotEmpty(message = "Seat IDs list cannot be empty")
    @Size(max = 8, message = "Maximum 8 seats per booking transaction")
    private List<String> seatIds;
}

