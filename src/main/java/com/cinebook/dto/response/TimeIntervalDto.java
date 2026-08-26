package com.cinebook.dto.response;

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
public class TimeIntervalDto {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String type; // SHOWTIME, TURNAROUND, AVAILABLE
    private String showtimeId;
    private String movieTitle;
}