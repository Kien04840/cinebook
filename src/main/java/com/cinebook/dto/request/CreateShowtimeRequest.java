package com.cinebook.dto.request;

import com.cinebook.enums.ShowtimeFormat;
import com.cinebook.enums.ShowtimeStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateShowtimeRequest {

    @NotBlank(message = "Movie ID is required")
    private String movieId;

    @NotBlank(message = "Auditorium ID is required")
    private String auditoriumId;

    @NotNull(message = "Showtime format is required")
    private ShowtimeFormat format;

    @NotBlank(message = "Language is required")
    @Size(max = 20, message = "Language cannot exceed 20 characters")
    private String language;

    @Size(max = 30, message = "Subtitle cannot exceed 30 characters")
    private String subtitle;

    @NotNull(message = "Start time is required")
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0.0", message = "Base price cannot be negative")
    private BigDecimal basePrice;

    private ShowtimeStatus status;
}