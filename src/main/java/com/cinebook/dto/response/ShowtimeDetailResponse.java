package com.cinebook.dto.response;

import com.cinebook.enums.ShowtimeFormat;
import com.cinebook.enums.ShowtimeStatus;
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
public class ShowtimeDetailResponse {

    private String id;
    private MovieSummaryResponse movie;
    private CinemaSummaryResponse cinema;
    private AuditoriumResponse auditorium;
    private ShowtimeFormat format;
    private String language;
    private String subtitle;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal basePrice;
    private ShowtimeStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}