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
public class TmdbMovieImportResponse {

    private String movieId;
    private Long tmdbId;
    private String title;
    private String originalTitle;
    private String action;
    private MovieStatus status;
    private LocalDate releaseDate;
    private String ageRating;
    private List<String> genres;
    private String posterUrl;
    private String trailerUrl;
}