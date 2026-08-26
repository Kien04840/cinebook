package com.cinebook.dto.request;

import com.cinebook.enums.ShowtimeFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowtimeGenerationRequest {

    @NotNull(message = "Movie ID is required")
    private String movieId;

    @NotEmpty(message = "At least one auditorium ID is required")
    private List<String> auditoriumIds;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    private LocalDate endDate;

    private LocalTime openingTime;

    private LocalTime closingTime;

    private Short snapIntervalMinutes;

    private Short staggerIntervalMinutes;

    private ShowtimeFormat format;

    private String language;

    private String subtitle;

    @DecimalMin(value = "0.0", message = "Base price must be greater than or equal to 0")
    private BigDecimal basePrice;
}