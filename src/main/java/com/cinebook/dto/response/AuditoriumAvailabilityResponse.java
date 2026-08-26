package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditoriumAvailabilityResponse {
    private String auditoriumId;
    private String auditoriumName;
    private String cinemaId;
    private String cinemaName;
    private LocalDate date;
    private LocalTime openingTime;
    private LocalTime closingTime;
    private Short turnaroundMinutes;
    private Short snapIntervalMinutes;
    @Builder.Default
    private List<TimeIntervalDto> intervals = new ArrayList<>();
}