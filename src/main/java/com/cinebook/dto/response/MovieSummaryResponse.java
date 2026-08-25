package com.cinebook.dto.response;

import com.cinebook.enums.MovieStatus;
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
public class MovieSummaryResponse {

    private String id;
    private Long tmdbId;
    private String title;
    private String originalTitle;
    private String posterUrl;
    private String backdropUrl;
    private Short durationMinutes;
    private LocalDate releaseDate;
    private String ageRating;
    private MovieStatus status;
    private List<GenreResponse> genres;
}

