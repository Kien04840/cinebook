package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieSuggestionResponse {

    private String id;
    private String title;
    private String posterUrl;
    private Integer durationMinutes;
    private LocalDate releaseDate;
}

