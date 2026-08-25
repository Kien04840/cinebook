package com.cinebook.mapper;

import com.cinebook.dto.response.GenreResponse;
import com.cinebook.dto.response.MovieDetailResponse;
import com.cinebook.dto.response.MovieSummaryResponse;
import com.cinebook.entity.Movie;
import com.cinebook.entity.MovieGenre;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class MovieMapper {

    private final GenreMapper genreMapper;

    public MovieSummaryResponse toMovieSummaryResponse(Movie movie) {
        if (movie == null) {
            return null;
        }

        List<GenreResponse> genres = extractGenres(movie);

        return MovieSummaryResponse.builder()
                .id(movie.getId())
                .tmdbId(movie.getTmdbId())
                .title(movie.getTitle())
                .originalTitle(movie.getOriginalTitle())
                .posterUrl(movie.getPosterUrl())
                .backdropUrl(movie.getBackdropUrl())
                .durationMinutes(movie.getDurationMinutes())
                .releaseDate(movie.getReleaseDate())
                .ageRating(movie.getAgeRating())
                .status(movie.getStatus())
                .genres(genres)
                .build();
    }

    public MovieDetailResponse toMovieDetailResponse(Movie movie) {
        if (movie == null) {
            return null;
        }

        List<GenreResponse> genres = extractGenres(movie);

        return MovieDetailResponse.builder()
                .id(movie.getId())
                .tmdbId(movie.getTmdbId())
                .title(movie.getTitle())
                .originalTitle(movie.getOriginalTitle())
                .overview(movie.getOverview())
                .durationMinutes(movie.getDurationMinutes())
                .director(movie.getDirector())
                .actors(movie.getActors())
                .country(movie.getCountry())
                .language(movie.getLanguage())
                .releaseDate(movie.getReleaseDate())
                .ageRating(movie.getAgeRating())
                .posterUrl(movie.getPosterUrl())
                .backdropUrl(movie.getBackdropUrl())
                .trailerUrl(movie.getTrailerUrl())
                .status(movie.getStatus())
                .genres(genres)
                .createdAt(movie.getCreatedAt())
                .updatedAt(movie.getUpdatedAt())
                .build();
    }

    private List<GenreResponse> extractGenres(Movie movie) {
        if (movie.getMovieGenres() == null || movie.getMovieGenres().isEmpty()) {
            return Collections.emptyList();
        }

        return movie.getMovieGenres().stream()
                .map(MovieGenre::getGenre)
                .filter(Objects::nonNull)
                .map(genreMapper::toGenreResponse)
                .toList();
    }
}

