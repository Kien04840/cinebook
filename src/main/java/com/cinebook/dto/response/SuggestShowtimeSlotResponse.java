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
public class SuggestShowtimeSlotResponse {
    private boolean available;
    private LocalDateTime suggestedStartTime;
    private LocalDateTime suggestedEndTime;
    private Short movieDurationMinutes;
    private LocalDateTime occupancyEndTime;
    private String message;
}