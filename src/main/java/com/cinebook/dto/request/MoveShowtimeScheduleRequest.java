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
public class MoveShowtimeScheduleRequest {

    private String auditoriumId;

    @NotNull(message = "Start time is required")
    private LocalDateTime startTime;
}

