package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateShowtimeSlotResponse {
    private boolean valid;
    private LocalDateTime calculatedStartTime;
    private LocalDateTime calculatedEndTime;
    private Short movieDurationMinutes;
    private LocalDateTime occupancyEndTime;
    @Builder.Default
    private List<SchedulingConflictResponse> conflicts = new ArrayList<>();
}