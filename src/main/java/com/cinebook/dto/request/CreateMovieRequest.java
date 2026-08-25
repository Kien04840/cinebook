package com.cinebook.dto.request;

import com.cinebook.enums.MovieStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMovieRequest {

    private Long tmdbId;

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title cannot exceed 255 characters")
    private String title;

    @Size(max = 255, message = "Original title cannot exceed 255 characters")
    private String originalTitle;

    @NotBlank(message = "Overview is required")
    @Size(max = 2000, message = "Overview cannot exceed 2000 characters")
    private String overview;

    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be at least 1 minute")
    private Short durationMinutes;

    @NotBlank(message = "Director is required")
    @Size(max = 255, message = "Director cannot exceed 255 characters")
    private String director;

    @NotBlank(message = "Actors are required")
    @Size(max = 1000, message = "Actors list cannot exceed 1000 characters")
    private String actors;

    @Size(max = 100, message = "Country cannot exceed 100 characters")
    private String country;

    @Size(max = 100, message = "Language cannot exceed 100 characters")
    private String language;

    @NotNull(message = "Release date is required")
    private LocalDate releaseDate;

    @NotBlank(message = "Age rating is required")
    @Size(max = 10, message = "Age rating cannot exceed 10 characters")
    private String ageRating;

    @Size(max = 500, message = "Poster URL cannot exceed 500 characters")
    private String posterUrl;

    @Size(max = 500, message = "Backdrop URL cannot exceed 500 characters")
    private String backdropUrl;

    @Size(max = 500, message = "Trailer URL cannot exceed 500 characters")
    private String trailerUrl;

    @NotNull(message = "Status is required")
    private MovieStatus status;

    private Set<String> genreIds;
}

