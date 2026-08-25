package com.cinebook.controller;

import com.cinebook.dto.request.CreateMovieRequest;
import com.cinebook.dto.request.UpdateMovieRequest;
import com.cinebook.dto.response.MovieDetailResponse;
import com.cinebook.dto.response.MovieSummaryResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.enums.MovieStatus;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.MovieService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminMovieControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private MovieService movieService;

    @InjectMocks
    private AdminMovieController adminMovieController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminMovieController)
                .setCustomArgumentResolvers(new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAdminMovies_Returns200() throws Exception {
        MovieSummaryResponse summary = MovieSummaryResponse.builder()
                .id("movie-1")
                .title("Interstellar")
                .status(MovieStatus.COMING_SOON)
                .build();

        PageResponse<MovieSummaryResponse> pageResponse = PageResponse.<MovieSummaryResponse>builder()
                .content(List.of(summary))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(movieService.getAdminMovies(any(), any(), any(), any(), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("movie-1"))
                .andExpect(jsonPath("$.content[0].title").value("Interstellar"));
    }

    @Test
    void createMovie_ValidRequest_Returns201() throws Exception {
        CreateMovieRequest request = CreateMovieRequest.builder()
                .title("Oppenheimer")
                .overview("The story of American scientist J. Robert Oppenheimer.")
                .durationMinutes((short) 180)
                .director("Christopher Nolan")
                .actors("Cillian Murphy, Emily Blunt")
                .releaseDate(LocalDate.of(2023, 7, 21))
                .ageRating("R")
                .status(MovieStatus.NOW_SHOWING)
                .genreIds(Set.of("genre-1"))
                .build();

        MovieDetailResponse response = MovieDetailResponse.builder()
                .id("movie-oppenheimer")
                .title("Oppenheimer")
                .director("Christopher Nolan")
                .status(MovieStatus.NOW_SHOWING)
                .build();

        when(movieService.createMovie(any(CreateMovieRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("movie-oppenheimer"))
                .andExpect(jsonPath("$.title").value("Oppenheimer"));
    }

    @Test
    void createMovie_InvalidRequest_Returns400() throws Exception {
        CreateMovieRequest request = CreateMovieRequest.builder()
                .title("") // Blank title
                .durationMinutes((short) 0) // Invalid duration
                .build();

        mockMvc.perform(post("/api/v1/admin/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updateMovie_ValidRequest_Returns200() throws Exception {
        UpdateMovieRequest request = UpdateMovieRequest.builder()
                .title("Oppenheimer (Updated)")
                .overview("Updated overview")
                .durationMinutes((short) 180)
                .director("Christopher Nolan")
                .actors("Cillian Murphy")
                .releaseDate(LocalDate.of(2023, 7, 21))
                .ageRating("R")
                .status(MovieStatus.NOW_SHOWING)
                .build();

        MovieDetailResponse response = MovieDetailResponse.builder()
                .id("movie-1")
                .title("Oppenheimer (Updated)")
                .build();

        when(movieService.updateMovie(eq("movie-1"), any(UpdateMovieRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/movies/movie-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Oppenheimer (Updated)"));
    }

    @Test
    void deleteMovie_Returns204() throws Exception {
        doNothing().when(movieService).deleteMovie("movie-1");

        mockMvc.perform(delete("/api/v1/admin/movies/movie-1"))
                .andExpect(status().isNoContent());
    }
}
