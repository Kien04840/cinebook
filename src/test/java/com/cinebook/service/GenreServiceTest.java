package com.cinebook.service;

import com.cinebook.dto.request.CreateGenreRequest;
import com.cinebook.dto.request.UpdateGenreRequest;
import com.cinebook.dto.response.GenreResponse;
import com.cinebook.entity.Genre;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.GenreMapper;
import com.cinebook.repository.GenreRepository;
import com.cinebook.repository.MovieGenreRepository;
import com.cinebook.service.impl.GenreServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenreServiceTest {

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private MovieGenreRepository movieGenreRepository;

    @Spy
    private GenreMapper genreMapper = new GenreMapper();

    @InjectMocks
    private GenreServiceImpl genreService;

    private Genre sampleGenre;

    @BeforeEach
    void setUp() {
        sampleGenre = new Genre();
        sampleGenre.setId("genre-1");
        sampleGenre.setName("Action");
        sampleGenre.setDescription("Action movies");
    }

    @Test
    void getAllGenres_Success() {
        when(genreRepository.findAll()).thenReturn(List.of(sampleGenre));

        List<GenreResponse> result = genreService.getAllGenres();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Action", result.get(0).getName());
    }

    @Test
    void getGenreById_Success() {
        when(genreRepository.findById("genre-1")).thenReturn(Optional.of(sampleGenre));

        GenreResponse result = genreService.getGenreById("genre-1");

        assertNotNull(result);
        assertEquals("genre-1", result.getId());
        assertEquals("Action", result.getName());
    }

    @Test
    void getGenreById_NotFound_ThrowsException() {
        when(genreRepository.findById("invalid-id")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> genreService.getGenreById("invalid-id"));
    }

    @Test
    void createGenre_Success() {
        CreateGenreRequest request = CreateGenreRequest.builder()
                .name("Comedy")
                .description("Funny movies")
                .build();

        when(genreRepository.existsByName("Comedy")).thenReturn(false);
        when(genreRepository.save(any(Genre.class))).thenAnswer(invocation -> {
            Genre g = invocation.getArgument(0);
            g.setId("genre-new");
            return g;
        });

        GenreResponse result = genreService.createGenre(request);

        assertNotNull(result);
        assertEquals("Comedy", result.getName());
        assertEquals("Funny movies", result.getDescription());
        verify(genreRepository).save(any(Genre.class));
    }

    @Test
    void createGenre_DuplicateName_ThrowsConflict() {
        CreateGenreRequest request = CreateGenreRequest.builder()
                .name("Action")
                .description("Action movies")
                .build();

        when(genreRepository.existsByName("Action")).thenReturn(true);

        assertThrows(ConflictException.class, () -> genreService.createGenre(request));
        verify(genreRepository, never()).save(any());
    }

    @Test
    void updateGenre_Success() {
        UpdateGenreRequest request = UpdateGenreRequest.builder()
                .name("Action & Adventure")
                .description("Updated description")
                .build();

        when(genreRepository.findById("genre-1")).thenReturn(Optional.of(sampleGenre));
        when(genreRepository.existsByName("Action & Adventure")).thenReturn(false);
        when(genreRepository.save(any(Genre.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GenreResponse result = genreService.updateGenre("genre-1", request);

        assertNotNull(result);
        assertEquals("Action & Adventure", result.getName());
        assertEquals("Updated description", result.getDescription());
    }

    @Test
    void updateGenre_DuplicateName_ThrowsConflict() {
        UpdateGenreRequest request = UpdateGenreRequest.builder()
                .name("Drama")
                .description("Dramatic movies")
                .build();

        when(genreRepository.findById("genre-1")).thenReturn(Optional.of(sampleGenre));
        when(genreRepository.existsByName("Drama")).thenReturn(true);

        assertThrows(ConflictException.class, () -> genreService.updateGenre("genre-1", request));
    }

    @Test
    void deleteGenre_Success() {
        when(genreRepository.findById("genre-1")).thenReturn(Optional.of(sampleGenre));

        genreService.deleteGenre("genre-1");

        verify(movieGenreRepository).deleteByGenreId("genre-1");
        verify(genreRepository).delete(sampleGenre);
    }
}

