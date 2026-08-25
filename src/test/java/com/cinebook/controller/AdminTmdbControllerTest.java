package com.cinebook.controller;

import com.cinebook.dto.response.TmdbGenreSyncResponse;
import com.cinebook.dto.response.TmdbMovieImportResponse;
import com.cinebook.enums.MovieStatus;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.exception.TmdbResourceNotFoundException;
import com.cinebook.exception.TmdbServiceException;
import com.cinebook.service.TmdbImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for AdminTmdbController using standalone MockMvc (no Spring Security).
 * Security (401/403) is enforced by SecurityConfig at the filter chain level;
 * that behavior is covered by the existing MovieSecurityTest integration pattern.
 */
@ExtendWith(MockitoExtension.class)
class AdminTmdbControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TmdbImportService tmdbImportService;

    @InjectMocks
    private AdminTmdbController adminTmdbController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminTmdbController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // =========================================================
    //  Genre Sync
    // =========================================================

    @Test
    void syncGenres_Returns200WithCounts() throws Exception {
        TmdbGenreSyncResponse syncResponse = TmdbGenreSyncResponse.builder()
                .created(5)
                .updated(2)
                .unchanged(3)
                .total(10)
                .build();

        when(tmdbImportService.syncGenres()).thenReturn(syncResponse);

        mockMvc.perform(post("/api/v1/admin/tmdb/genres/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(5))
                .andExpect(jsonPath("$.updated").value(2))
                .andExpect(jsonPath("$.unchanged").value(3))
                .andExpect(jsonPath("$.total").value(10));
    }

    @Test
    void syncGenres_TmdbServiceError_Returns503() throws Exception {
        when(tmdbImportService.syncGenres())
                .thenThrow(new TmdbServiceException("TMDB is down"));

        mockMvc.perform(post("/api/v1/admin/tmdb/genres/sync"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    // =========================================================
    //  Movie Import
    // =========================================================

    @Test
    void importMovie_Created_Returns200() throws Exception {
        TmdbMovieImportResponse importResponse = TmdbMovieImportResponse.builder()
                .movieId("cinebook-uuid-1")
                .tmdbId(550L)
                .title("Fight Club")
                .originalTitle("Fight Club")
                .action("CREATED")
                .status(MovieStatus.NOW_SHOWING)
                .releaseDate(LocalDate.of(1999, 10, 15))
                .ageRating("R")
                .genres(List.of("Action"))
                .posterUrl("https://image.tmdb.org/t/p/w500/poster.jpg")
                .build();

        when(tmdbImportService.importMovie(550L)).thenReturn(importResponse);

        mockMvc.perform(post("/api/v1/admin/tmdb/movies/550/import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movieId").value("cinebook-uuid-1"))
                .andExpect(jsonPath("$.tmdbId").value(550))
                .andExpect(jsonPath("$.title").value("Fight Club"))
                .andExpect(jsonPath("$.action").value("CREATED"))
                .andExpect(jsonPath("$.status").value("NOW_SHOWING"))
                .andExpect(jsonPath("$.ageRating").value("R"))
                .andExpect(jsonPath("$.genres[0]").value("Action"));
    }

    @Test
    void importMovie_Updated_Returns200() throws Exception {
        TmdbMovieImportResponse importResponse = TmdbMovieImportResponse.builder()
                .movieId("existing-uuid")
                .tmdbId(550L)
                .title("Fight Club")
                .action("UPDATED")
                .status(MovieStatus.HIDDEN) // admin-set status preserved
                .build();

        when(tmdbImportService.importMovie(550L)).thenReturn(importResponse);

        mockMvc.perform(post("/api/v1/admin/tmdb/movies/550/import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("UPDATED"))
                .andExpect(jsonPath("$.status").value("HIDDEN"));
    }

    @Test
    void importMovie_TmdbNotFound_Returns404() throws Exception {
        when(tmdbImportService.importMovie(99999L))
                .thenThrow(new TmdbResourceNotFoundException(99999L));

        mockMvc.perform(post("/api/v1/admin/tmdb/movies/99999/import"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void importMovie_TmdbServiceError_Returns503() throws Exception {
        when(tmdbImportService.importMovie(550L))
                .thenThrow(new TmdbServiceException("Connection timeout"));

        mockMvc.perform(post("/api/v1/admin/tmdb/movies/550/import"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }
}
