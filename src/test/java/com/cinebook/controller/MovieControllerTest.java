package com.cinebook.controller;

import com.cinebook.dto.response.MovieDetailResponse;
import com.cinebook.dto.response.MovieSummaryResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.enums.MovieStatus;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.MovieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MovieControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MovieService movieService;

    @InjectMocks
    private MovieController movieController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(movieController)
                .setCustomArgumentResolvers(new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getPublicMovies_Returns200() throws Exception {
        MovieSummaryResponse movieSummary = MovieSummaryResponse.builder()
                .id("movie-1")
                .title("Inception")
                .durationMinutes((short) 148)
                .releaseDate(LocalDate.of(2010, 7, 16))
                .status(MovieStatus.NOW_SHOWING)
                .build();

        PageResponse<MovieSummaryResponse> pageResponse = PageResponse.<MovieSummaryResponse>builder()
                .content(List.of(movieSummary))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(movieService.getPublicMovies(eq("Inception"), eq("Action"), eq(MovieStatus.NOW_SHOWING), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/movies")
                        .param("q", "Inception")
                        .param("genre", "Action")
                        .param("status", "NOW_SHOWING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("movie-1"))
                .andExpect(jsonPath("$.content[0].title").value("Inception"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getMovieDetail_Returns200() throws Exception {
        MovieDetailResponse detailResponse = MovieDetailResponse.builder()
                .id("movie-1")
                .title("Inception")
                .director("Christopher Nolan")
                .durationMinutes((short) 148)
                .status(MovieStatus.NOW_SHOWING)
                .build();

        when(movieService.getMovieDetail("movie-1")).thenReturn(detailResponse);

        mockMvc.perform(get("/api/v1/movies/movie-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("movie-1"))
                .andExpect(jsonPath("$.title").value("Inception"))
                .andExpect(jsonPath("$.director").value("Christopher Nolan"));
    }
}
