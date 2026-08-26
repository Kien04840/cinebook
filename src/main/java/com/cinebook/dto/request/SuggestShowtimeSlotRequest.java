package com.cinebook.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestShowtimeSlotRequest {

    @NotNull(message = "Movie ID is required")
    private String movieId;

    @NotNull(message = "Auditorium ID is required")
    private String auditoriumId;

    @NotNull(message = "Requested start time is required")
    private LocalDateTime requestedStartTime;

    private Short snapIntervalMinutes;
}