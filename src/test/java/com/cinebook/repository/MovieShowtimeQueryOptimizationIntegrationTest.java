package com.cinebook.repository;

import com.cinebook.dto.response.MovieDetailResponse;
import com.cinebook.dto.response.MovieSummaryResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.ShowtimeDetailResponse;
import com.cinebook.dto.response.ShowtimeSummaryResponse;
import com.cinebook.entity.Movie;
import com.cinebook.entity.Showtime;
import com.cinebook.enums.MovieStatus;
import com.cinebook.enums.ShowtimeStatus;
import com.cinebook.service.MovieService;
import com.cinebook.service.ShowtimeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MovieShowtimeQueryOptimizationIntegrationTest {

    @Autowired
    private MovieService movieService;

    @Autowired
    private ShowtimeService showtimeService;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        entityManager.clear();
    }

    @Test
    @DisplayName("TASK-02: ShowtimeRepository.findAll(Specification, Pageable) with @EntityGraph must execute <= 2 queries and eagerly load Movie, Auditorium, and Cinema")
    void testShowtimeFindAllWithEntityGraph() {
        entityManager.clear();
        statistics.clear();

        Specification<Showtime> spec = (root, query, cb) -> cb.conjunction();
        Page<Showtime> page = showtimeRepository.findAll(spec, PageRequest.of(0, 10));

        long queryCountBeforeTraversal = statistics.getPrepareStatementCount();

        assertThat(page).isNotNull();
        assertThat(page.getContent()).isNotEmpty();

        // 1 count query + 1 entity query with @EntityGraph left join (movie, auditorium, cinema)
        assertThat(queryCountBeforeTraversal)
                .as("Showtime page fetching with @EntityGraph must execute <= 2 queries (1 count + 1 joined fetch)")
                .isLessThanOrEqualTo(2L);

        // Traverse lazy associations for every showtime on the page
        // Without @EntityGraph, this traversal triggers 10 movie + 10 auditorium + 10 cinema = 30 extra queries!
        for (Showtime s : page.getContent()) {
            assertThat(s.getMovie()).isNotNull();
            assertThat(s.getMovie().getTitle()).isNotBlank();
            assertThat(s.getAuditorium()).isNotNull();
            assertThat(s.getAuditorium().getName()).isNotBlank();
            assertThat(s.getAuditorium().getCinema()).isNotNull();
            assertThat(s.getAuditorium().getCinema().getName()).isNotBlank();
        }

        long queryCountAfterTraversal = statistics.getPrepareStatementCount();

        assertThat(queryCountAfterTraversal)
                .as("Accessing movie, auditorium, and cinema must NOT trigger any additional queries (0 lazy load queries)")
                .isEqualTo(queryCountBeforeTraversal);
    }

    @Test
    @DisplayName("TASK-02: ShowtimeRepository.findByStatus with @EntityGraph must eagerly load Movie, Auditorium, and Cinema without N+1")
    void testShowtimeFindByStatusWithEntityGraph() {
        entityManager.clear();
        statistics.clear();

        Page<Showtime> page = showtimeRepository.findByStatus(ShowtimeStatus.SCHEDULED, PageRequest.of(0, 10));

        long queryCountBeforeTraversal = statistics.getPrepareStatementCount();

        assertThat(page).isNotNull();
        assertThat(queryCountBeforeTraversal)
                .as("findByStatus must execute <= 2 queries with @EntityGraph")
                .isLessThanOrEqualTo(2L);

        for (Showtime s : page.getContent()) {
            assertThat(s.getMovie()).isNotNull();
            assertThat(s.getAuditorium()).isNotNull();
            assertThat(s.getAuditorium().getCinema()).isNotNull();
        }

        assertThat(statistics.getPrepareStatementCount())
                .as("Traversing associations must trigger zero extra queries")
                .isEqualTo(queryCountBeforeTraversal);
    }

    @Test
    @DisplayName("TASK-02: ShowtimeRepository.findById should eagerly fetch movie, genres, auditorium, and cinema in exactly 1 SQL statement")
    void testShowtimeFindByIdSingleQuery() {
        List<Showtime> activeShowtimes = showtimeRepository.findByStatus(ShowtimeStatus.SCHEDULED, PageRequest.of(0, 1)).getContent();
        if (activeShowtimes.isEmpty()) {
            return;
        }
        String showtimeId = activeShowtimes.get(0).getId();

        entityManager.clear();
        statistics.clear();

        Showtime showtime = showtimeRepository.findById(showtimeId).orElseThrow();
        long statementCount = statistics.getPrepareStatementCount();

        assertThat(statementCount)
                .as("Single showtime lookup with @EntityGraph must execute exactly 1 query")
                .isEqualTo(1L);

        assertThat(showtime.getMovie()).isNotNull();
        assertThat(showtime.getMovie().getTitle()).isNotBlank();
        assertThat(showtime.getAuditorium()).isNotNull();
        assertThat(showtime.getAuditorium().getCinema()).isNotNull();
    }

    @Test
    @DisplayName("TASK-02: getPublicMovies pagination must use batch fetching (<= 4 queries) without N+1 or HHH000104 warnings")
    void testPublicMoviesPaginationBatchFetching() {
        entityManager.clear();
        statistics.clear();

        PageResponse<MovieSummaryResponse> response = movieService.getPublicMovies(
                null, null, null, PageRequest.of(0, 10));

        long statementCount = statistics.getPrepareStatementCount();

        assertThat(response).isNotNull();
        // 1 count query + 1 movie page query + at most 1 batch query for movie_genres + at most 1 batch query for genres
        // With N+1 problem, this would have been 1 + 1 + (N * 2) = 22 queries.
        assertThat(statementCount)
                .as("Movie pagination must batch fetch genres (count + movies + movie_genres batch + genre batch <= 4 queries)")
                .isLessThanOrEqualTo(4L);

        if (!response.getContent().isEmpty()) {
            MovieSummaryResponse first = response.getContent().get(0);
            assertThat(first.getTitle()).isNotBlank();
        }
    }

    @Test
    @DisplayName("TASK-02: MovieRepository.findById should eagerly fetch movie and genres in 1 SQL statement")
    void testMovieFindByIdSingleQuery() {
        List<Movie> movies = movieRepository.findByStatus(MovieStatus.NOW_SHOWING, PageRequest.of(0, 1)).getContent();
        if (movies.isEmpty()) {
            return;
        }
        String movieId = movies.get(0).getId();

        entityManager.clear();
        statistics.clear();

        Movie movie = movieRepository.findById(movieId).orElseThrow();
        long statementCount = statistics.getPrepareStatementCount();

        assertThat(statementCount)
                .as("Single movie lookup with @EntityGraph must execute exactly 1 query")
                .isEqualTo(1L);

        assertThat(movie.getTitle()).isNotBlank();
        assertThat(movie.getMovieGenres()).isNotEmpty();
    }

    @Test
    @DisplayName("TASK-02: getMovieDetail should execute in 1 SQL query with genres populated")
    void testGetMovieDetailQueryCount() {
        List<Movie> movies = movieRepository.findByStatus(MovieStatus.NOW_SHOWING, PageRequest.of(0, 1)).getContent();
        if (movies.isEmpty()) {
            return;
        }
        String movieId = movies.get(0).getId();

        entityManager.clear();
        statistics.clear();

        MovieDetailResponse response = movieService.getMovieDetail(movieId);
        long statementCount = statistics.getPrepareStatementCount();

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isNotBlank();
        assertThat(response.getGenres()).isNotEmpty();
        assertThat(statementCount)
                .as("Movie detail retrieval should execute in 1 query")
                .isEqualTo(1L);
    }
}
