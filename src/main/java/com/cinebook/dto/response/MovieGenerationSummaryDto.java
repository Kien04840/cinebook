package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieGenerationSummaryDto {
    private String movieId;
    private String movieTitle;
    private Integer targetScreenings;
    private Integer scheduledScreenings;
    private Integer remainingScreenings;
}

