package com.cinebook.dto.request;

import com.cinebook.enums.ShowtimeFormat;
import jakarta.validation.constraints.NotNull;
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
public class ValidateShowtimeSlotRequest {

    @NotNull(message = "Movie ID is required")
    private String movieId;

    @NotNull(message = "Auditorium ID is required")
    private String auditoriumId;

    @NotNull(message = "Start time is required")
    private LocalDateTime startTime;

    private ShowtimeFormat format;

    private String language;

    private String subtitle;

    private String excludeShowtimeId;
}