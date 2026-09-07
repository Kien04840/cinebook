package com.cinebook.dto.request;

import com.cinebook.enums.ShowtimeFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieGenerationConfigDto {

    @NotBlank(message = "Movie ID is required")
    private String movieId;

    @NotNull(message = "Target screenings per day is required")
    @Min(value = 1, message = "Target screenings per day must be at least 1")
    private Integer targetScreeningsPerDay;

    private ShowtimeFormat format;

    private String language;

    private String subtitle;

    @DecimalMin(value = "0.0", message = "Base price must be greater than or equal to 0")
    private BigDecimal basePrice;
}

