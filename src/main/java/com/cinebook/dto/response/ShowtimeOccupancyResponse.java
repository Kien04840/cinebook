package com.cinebook.dto.response;

import com.cinebook.enums.ShowtimeFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowtimeOccupancyResponse {
    private String showtimeId;
    private String movieId;
    private String movieTitle;
    private String cinemaId;
    private String cinemaName;
    private String auditoriumId;
    private String auditoriumName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ShowtimeFormat format;
    private Integer totalCapacity;
    private Integer occupiedSeats;
    private Integer availableSeats;
    private BigDecimal occupancyRate;
}

