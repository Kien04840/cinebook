package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarAuditoriumShowtimesResponse {
    private String auditoriumId;
    private String auditoriumName;
    private String auditoriumType;
    @Builder.Default
    private List<ShowtimeSummaryResponse> showtimes = new ArrayList<>();
}