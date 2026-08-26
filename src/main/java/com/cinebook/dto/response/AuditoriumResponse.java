package com.cinebook.dto.response;

import com.cinebook.enums.AuditoriumStatus;
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
public class AuditoriumResponse {

    private String id;
    private String cinemaId;
    private String cinemaName;
    private String name;
    private String type;
    private Short rowsCount;
    private Short columnsCount;
    private int totalSeats;
    private AuditoriumStatus status;
    private Short turnaroundMinutes;
    private Short snapIntervalMinutes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}