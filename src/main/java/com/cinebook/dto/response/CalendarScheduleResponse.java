package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarScheduleResponse {
    private String cinemaId;
    private String cinemaName;
    private LocalDate from;
    private LocalDate to;
    @Builder.Default
    private List<CalendarAuditoriumShowtimesResponse> auditoriums = new ArrayList<>();
}