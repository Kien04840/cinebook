package com.cinebook.dto.response;

import com.cinebook.enums.SchedulingConflictType;
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
public class SchedulingConflictResponse {
    private SchedulingConflictType type;
    private String auditoriumId;
    private String auditoriumName;
    private String existingShowtimeId;
    private LocalDateTime conflictingStartTime;
    private LocalDateTime conflictingEndTime;
    private String message;
}