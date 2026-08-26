package com.cinebook.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopyScheduleRequest {

    @NotNull(message = "Source date is required")
    private LocalDate sourceDate;

    @NotNull(message = "Target date is required")
    private LocalDate targetDate;

    private String cinemaId;

    private List<String> auditoriumIds;
}