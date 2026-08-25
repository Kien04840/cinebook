package com.cinebook.dto.response;

import com.cinebook.enums.MovieStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieDetailResponse {

    private String id;
    private Long tmdbId;
    private String title;
    private String originalTitle;
    private String overview;
    private Short durationMinutes;
    private String director;
    private String actors;
    private String country;
    private String language;
    private LocalDate releaseDate;
    private String ageRating;
    private String posterUrl;
    private String backdropUrl;
    private String trailerUrl;
    private MovieStatus status;
    private List<GenreResponse> genres;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

