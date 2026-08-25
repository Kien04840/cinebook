package com.cinebook.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
public class BatchUpdateSeatTypeRequest {

    @NotEmpty(message = "Seat IDs list cannot be empty")
    private List<String> seatIds;

    @NotBlank(message = "Seat type ID is required")
    private String seatTypeId;
}