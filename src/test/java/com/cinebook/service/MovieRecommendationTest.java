package com.cinebook.service;

import com.cinebook.dto.response.MovieRecommendationResponse;
import com.cinebook.dto.response.MovieSummaryResponse;
import com.cinebook.entity.*;
import com.cinebook.enums.BookingStatus;
import com.cinebook.enums.MovieStatus;
import com.cinebook.mapper.GenreMapper;
import com.cinebook.mapper.MovieMapper;
import com.cinebook.repository.BookingRepository;
import com.cinebook.repository.GenreRepository;
import com.cinebook.repository.MovieRepository;
import com.cinebook.service.impl.MovieServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieRecommendationTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Spy
    private GenreMapper genreMapper = new GenreMapper();

    @Spy
    private MovieMapper movieMapper = new MovieMapper(new GenreMapper());

    @InjectMocks
    private MovieServiceImpl movieService;

    private Movie actionMovie;
    private Movie comedyMovie;
    private Genre actionGenre;
    private Genre comedyGenre;

    @BeforeEach
    void setUp() {
        actionGenre = new Genre();
        actionGenre.setId("g-action");
        actionGenre.setName("Hành động");

        comedyGenre = new Genre();
        comedyGenre.setId("g-comedy");
        comedyGenre.setName("Hài");

        actionMovie = new Movie();
        actionMovie.setId("m-action");
        actionMovie.setTitle("John Wick 4");
        actionMovie.setStatus(MovieStatus.NOW_SHOWING);
        actionMovie.setReleaseDate(LocalDate.of(2026, 8, 1));
        actionMovie.setOverview("Action film");
        actionMovie.setDurationMinutes((short) 120);
        actionMovie.setAgeRating("T18");

        MovieGenre mgAction = new MovieGenre();
        mgAction.setId(new MovieGenreId(actionMovie.getId(), actionGenre.getId()));
        mgAction.setMovie(actionMovie);
        mgAction.setGenre(actionGenre);
        actionMovie.setMovieGenres(Set.of(mgAction));

        comedyMovie = new Movie();
        comedyMovie.setId("m-comedy");
        comedyMovie.setTitle("Super Funny");
        comedyMovie.setStatus(MovieStatus.NOW_SHOWING);
        comedyMovie.setReleaseDate(LocalDate.of(2026, 8, 2));
        comedyMovie.setOverview("Comedy film");
        comedyMovie.setDurationMinutes((short) 95);
        comedyMovie.setAgeRating("P");

        MovieGenre mgComedy = new MovieGenre();
        mgComedy.setId(new MovieGenreId(comedyMovie.getId(), comedyGenre.getId()));
        mgComedy.setMovie(comedyMovie);
        mgComedy.setGenre(comedyGenre);
        comedyMovie.setMovieGenres(Set.of(mgComedy));
    }

    @Test
    @DisplayName("getMovieRecommendations - Fallback returns active movies when no bookings exist")
    void testGetMovieRecommendations_WithoutHistory_ReturnsFallbackHotMovies() {
        when(movieRepository.findAll(any(Specification.class))).thenReturn(List.of(actionMovie, comedyMovie));

        MovieRecommendationResponse response = movieService.getMovieRecommendations(6);

        assertThat(response).isNotNull();
        assertThat(response.getMovies()).hasSize(2);
        assertThat(response.getExplanation()).contains("nổi bật");
    }
}
