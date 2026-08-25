package com.cinebook.controller;

import com.cinebook.dto.response.GenreResponse;
import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.service.GenreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GenreControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GenreService genreService;

    @InjectMocks
    private GenreController genreController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(genreController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllGenres_Returns200() throws Exception {
        GenreResponse genreResponse = GenreResponse.builder()
                .id("genre-1")
                .name("Action")
                .description("Action movies")
                .build();

        when(genreService.getAllGenres()).thenReturn(List.of(genreResponse));

        mockMvc.perform(get("/api/v1/genres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("genre-1"))
                .andExpect(jsonPath("$[0].name").value("Action"));
    }
}

