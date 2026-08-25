package com.cinebook.security;

import com.cinebook.dto.request.CreateGenreRequest;
import com.cinebook.dto.request.CreateMovieRequest;
import com.cinebook.enums.MovieStatus;
import com.cinebook.service.GenreService;
import com.cinebook.service.MovieService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class MovieSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @MockitoBean
    private MovieService movieService;

    @MockitoBean
    private GenreService genreService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void getPublicMovies_Anonymous_Allowed() throws Exception {
        mockMvc.perform(get("/api/v1/movies"))
                .andExpect(status().isOk());
    }

    @Test
    void getPublicMovieDetail_Anonymous_Allowed() throws Exception {
        mockMvc.perform(get("/api/v1/movies/test-id"))
                .andExpect(status().isOk());
    }

    @Test
    void getPublicGenres_Anonymous_Allowed() throws Exception {
        mockMvc.perform(get("/api/v1/genres"))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpoints_Anonymous_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/movies"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/admin/movies/test-id"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/genres"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void adminEndpoints_CustomerRole_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/movies"))
                .andExpect(status().isForbidden());

        CreateMovieRequest request = CreateMovieRequest.builder()
                .title("Test")
                .overview("Overview")
                .durationMinutes((short) 120)
                .director("Director")
                .actors("Actors")
                .releaseDate(LocalDate.now())
                .ageRating("P")
                .status(MovieStatus.NOW_SHOWING)
                .build();

        mockMvc.perform(post("/api/v1/admin/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/admin/movies/test-id"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/genres"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminEndpoints_AdminRole_Allowed() throws Exception {
        mockMvc.perform(get("/api/v1/admin/movies"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/genres"))
                .andExpect(status().isOk());

        CreateGenreRequest genreRequest = CreateGenreRequest.builder()
                .name("New Genre")
                .build();

        mockMvc.perform(post("/api/v1/admin/genres")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(genreRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/admin/movies/test-id"))
                .andExpect(status().isNoContent());
    }
}
