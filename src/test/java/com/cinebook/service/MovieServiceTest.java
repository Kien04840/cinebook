package com.cinebook.service;

import com.cinebook.dto.request.CreateMovieRequest;
import com.cinebook.dto.request.UpdateMovieRequest;
import com.cinebook.dto.response.MovieDetailResponse;
import com.cinebook.dto.response.MovieSummaryResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.entity.Genre;
import com.cinebook.entity.Movie;
import com.cinebook.entity.MovieGenre;
import com.cinebook.entity.MovieGenreId;
import com.cinebook.enums.MovieStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.GenreMapper;
import com.cinebook.mapper.MovieMapper;
import com.cinebook.repository.GenreRepository;
import com.cinebook.repository.MovieRepository;
import com.cinebook.service.impl.MovieServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private com.cinebook.repository.BookingRepository bookingRepository;

    @Spy
    private GenreMapper genreMapper = new GenreMapper();

    @Spy
    private MovieMapper movieMapper = new MovieMapper(new GenreMapper());

    @InjectMocks
    private MovieServiceImpl movieService;

    private Movie sampleMovie;
    private Genre actionGenre;
    private Genre comedyGenre;

    @BeforeEach
    void setUp() {
        actionGenre = new Genre();
        actionGenre.setId("genre-action");
        actionGenre.setName("Action");

        comedyGenre = new Genre();
        comedyGenre.setId("genre-comedy");
        comedyGenre.setName("Comedy");

        sampleMovie = new Movie();
        sampleMovie.setId("movie-1");
        sampleMovie.setTitle("Inception");
        sampleMovie.setOriginalTitle("Inception Original");
        sampleMovie.setOverview("A thief who steals corporate secrets through dream-sharing technology.");
        sampleMovie.setDurationMinutes((short) 148);
        sampleMovie.setDirector("Christopher Nolan");
        sampleMovie.setActors("Leonardo DiCaprio, Joseph Gordon-Levitt");
        sampleMovie.setReleaseDate(LocalDate.of(2010, 7, 16));
        sampleMovie.setAgeRating("PG-13");
        sampleMovie.setStatus(MovieStatus.NOW_SHOWING);
        sampleMovie.setCreatedAt(LocalDateTime.now());
        sampleMovie.setUpdatedAt(LocalDateTime.now());

        MovieGenre movieGenre = new MovieGenre();
        movieGenre.setId(new MovieGenreId("movie-1", "genre-action"));
        movieGenre.setMovie(sampleMovie);
        movieGenre.setGenre(actionGenre);

        sampleMovie.setMovieGenres(new HashSet<>(Set.of(movieGenre)));
    }

    @Test
    void getPublicMovies_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Movie> page = new PageImpl<>(List.of(sampleMovie), pageable, 1);

        when(movieRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        PageResponse<MovieSummaryResponse> result = movieService.getPublicMovies("Inception", "Action", MovieStatus.NOW_SHOWING, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Inception", result.getContent().get(0).getTitle());
        assertEquals(1, result.getContent().get(0).getGenres().size());
    }

    @Test
    void getMovieDetail_Success() {
        when(movieRepository.findById("movie-1")).thenReturn(Optional.of(sampleMovie));

        MovieDetailResponse result = movieService.getMovieDetail("movie-1");

        assertNotNull(result);
        assertEquals("Inception", result.getTitle());
        assertEquals("Christopher Nolan", result.getDirector());
        assertEquals(1, result.getGenres().size());
        assertEquals("Action", result.getGenres().get(0).getName());
    }

    @Test
    void getMovieDetail_NotFound_ThrowsException() {
        when(movieRepository.findById("invalid-id")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> movieService.getMovieDetail("invalid-id"));
    }

    @Test
    void getMovieDetail_DeletedMovie_ThrowsNotFound() {
        sampleMovie.setDeletedAt(LocalDateTime.now());
        when(movieRepository.findById("movie-1")).thenReturn(Optional.of(sampleMovie));

        assertThrows(ResourceNotFoundException.class, () -> movieService.getMovieDetail("movie-1"));
    }

    @Test
    void getMovieDetail_HiddenMovie_ThrowsNotFound() {
        sampleMovie.setStatus(MovieStatus.HIDDEN);
        when(movieRepository.findById("movie-1")).thenReturn(Optional.of(sampleMovie));

        assertThrows(ResourceNotFoundException.class, () -> movieService.getMovieDetail("movie-1"));
    }

    @Test
    void createMovie_Success() {
        CreateMovieRequest request = CreateMovieRequest.builder()
                .title("Interstellar")
                .originalTitle("Interstellar")
                .overview("A team of explorers travel through a wormhole in space.")
                .durationMinutes((short) 169)
                .director("Christopher Nolan")
                .actors("Matthew McConaughey, Anne Hathaway")
                .country("USA")
                .language("English")
                .releaseDate(LocalDate.of(2014, 11, 7))
                .ageRating("PG-13")
                .status(MovieStatus.COMING_SOON)
                .tmdbId(157336L)
                .genreIds(Set.of("genre-action"))
                .build();

        when(movieRepository.existsByTmdbId(157336L)).thenReturn(false);
        when(genreRepository.findAllById(Set.of("genre-action"))).thenReturn(List.of(actionGenre));
        when(movieRepository.save(any(Movie.class))).thenAnswer(invocation -> {
            Movie m = invocation.getArgument(0);
            if (m.getId() == null) {
                m.setId("movie-new");
            }
            return m;
        });

        MovieDetailResponse result = movieService.createMovie(request);

        assertNotNull(result);
        assertEquals("Interstellar", result.getTitle());
        verify(movieRepository, atLeastOnce()).save(any(Movie.class));
    }

    @Test
    void createMovie_DuplicateTmdbId_ThrowsConflict() {
        CreateMovieRequest request = CreateMovieRequest.builder()
                .title("Interstellar")
                .tmdbId(157336L)
                .build();

        when(movieRepository.existsByTmdbId(157336L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> movieService.createMovie(request));
        verify(movieRepository, never()).save(any());
    }

    @Test
    void createMovie_InvalidGenreId_ThrowsBadRequest() {
        CreateMovieRequest request = CreateMovieRequest.builder()
                .title("Interstellar")
                .tmdbId(157336L)
                .genreIds(Set.of("invalid-genre-id"))
                .build();

        when(movieRepository.existsByTmdbId(157336L)).thenReturn(false);
        when(genreRepository.findAllById(Set.of("invalid-genre-id"))).thenReturn(List.of());

        assertThrows(BadRequestException.class, () -> movieService.createMovie(request));
    }

    @Test
    void updateMovie_Success_WithGenreSync() {
        UpdateMovieRequest request = UpdateMovieRequest.builder()
                .title("Inception (Updated)")
                .overview("Updated overview")
                .durationMinutes((short) 150)
                .director("Christopher Nolan")
                .actors("Leonardo DiCaprio")
                .releaseDate(LocalDate.of(2010, 7, 16))
                .ageRating("PG-13")
                .status(MovieStatus.NOW_SHOWING)
                .genreIds(Set.of("genre-comedy")) // Remove action, add comedy
                .build();

        when(movieRepository.findById("movie-1")).thenReturn(Optional.of(sampleMovie));
        when(genreRepository.findAllById(Set.of("genre-comedy"))).thenReturn(List.of(comedyGenre));
        when(movieRepository.save(any(Movie.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MovieDetailResponse result = movieService.updateMovie("movie-1", request);

        assertNotNull(result);
        assertEquals("Inception (Updated)", result.getTitle());
        assertEquals(1, result.getGenres().size());
        assertEquals("Comedy", result.getGenres().get(0).getName());
    }

    @Test
    void deleteMovie_SoftDelete_Success() {
        when(movieRepository.findById("movie-1")).thenReturn(Optional.of(sampleMovie));
        when(movieRepository.save(any(Movie.class))).thenAnswer(invocation -> invocation.getArgument(0));

        movieService.deleteMovie("movie-1");

        assertNotNull(sampleMovie.getDeletedAt());
        assertEquals(MovieStatus.HIDDEN, sampleMovie.getStatus());
        verify(movieRepository).save(sampleMovie);
    }
}

