package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CinemaSchedulingConfigResponse {
    private String cinemaId;
    private String cinemaName;
    private LocalTime openingTime;
    private LocalTime closingTime;
    @Builder.Default
    private List<AuditoriumSchedulingConfigResponse> auditoriums = new ArrayList<>();
}