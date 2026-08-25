package com.cinebook.controller;

import com.cinebook.dto.request.CreateGenreRequest;
import com.cinebook.dto.request.UpdateGenreRequest;
import com.cinebook.dto.response.GenreResponse;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.GenreService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminGenreControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private GenreService genreService;

    @InjectMocks
    private AdminGenreController adminGenreController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminGenreController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllGenres_Returns200() throws Exception {
        GenreResponse genreResponse = GenreResponse.builder()
                .id("genre-1")
                .name("Drama")
                .build();

        when(genreService.getAllGenres()).thenReturn(List.of(genreResponse));

        mockMvc.perform(get("/api/v1/admin/genres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("genre-1"))
                .andExpect(jsonPath("$[0].name").value("Drama"));
    }

    @Test
    void createGenre_ValidRequest_Returns201() throws Exception {
        CreateGenreRequest request = CreateGenreRequest.builder()
                .name("Sci-Fi")
                .description("Science Fiction")
                .build();

        GenreResponse response = GenreResponse.builder()
                .id("genre-new")
                .name("Sci-Fi")
                .description("Science Fiction")
                .build();

        when(genreService.createGenre(any(CreateGenreRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/genres")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("genre-new"))
                .andExpect(jsonPath("$.name").value("Sci-Fi"));
    }

    @Test
    void createGenre_InvalidName_Returns400() throws Exception {
        CreateGenreRequest request = CreateGenreRequest.builder()
                .name("")
                .build();

        mockMvc.perform(post("/api/v1/admin/genres")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("name"));
    }

    @Test
    void updateGenre_ValidRequest_Returns200() throws Exception {
        UpdateGenreRequest request = UpdateGenreRequest.builder()
                .name("Sci-Fi & Fantasy")
                .description("Science Fiction and Fantasy")
                .build();

        GenreResponse response = GenreResponse.builder()
                .id("genre-1")
                .name("Sci-Fi & Fantasy")
                .description("Science Fiction and Fantasy")
                .build();

        when(genreService.updateGenre(eq("genre-1"), any(UpdateGenreRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/genres/genre-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sci-Fi & Fantasy"));
    }

    @Test
    void deleteGenre_Returns204() throws Exception {
        doNothing().when(genreService).deleteGenre("genre-1");

        mockMvc.perform(delete("/api/v1/admin/genres/genre-1"))
                .andExpect(status().isNoContent());
    }
}

