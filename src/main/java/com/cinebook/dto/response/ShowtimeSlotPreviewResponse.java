package com.cinebook.dto.response;

import com.cinebook.enums.ShowtimeFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowtimeSlotPreviewResponse {
    private LocalDate date;
    private String auditoriumId;
    private String auditoriumName;
    private String movieId;
    private String movieTitle;
    private Short movieDurationMinutes;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ShowtimeFormat format;
    private String language;
    private String subtitle;
    private BigDecimal basePrice;
    private boolean valid;
    @Builder.Default
    private List<SchedulingConflictResponse> conflicts = new ArrayList<>();
}