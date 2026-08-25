package com.cinebook.service;

import com.cinebook.config.TmdbProperties;
import com.cinebook.dto.response.TmdbGenreSyncResponse;
import com.cinebook.dto.response.TmdbMovieImportResponse;
import com.cinebook.dto.tmdb.TmdbGenreDto;
import com.cinebook.dto.tmdb.TmdbGenreListResponse;
import com.cinebook.dto.tmdb.TmdbMovieDetailDto;
import com.cinebook.entity.Genre;
import com.cinebook.entity.Movie;
import com.cinebook.entity.MovieGenre;
import com.cinebook.entity.MovieGenreId;
import com.cinebook.enums.MovieStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.TmdbResourceNotFoundException;
import com.cinebook.repository.GenreRepository;
import com.cinebook.repository.MovieRepository;
import com.cinebook.service.impl.TmdbImportServiceImpl;
import com.cinebook.tmdb.TmdbClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TmdbImportServiceTest {

    @Mock
    private TmdbClient tmdbClient;

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private MovieRepository movieRepository;

    @Spy
    private TmdbProperties tmdbProperties = new TmdbProperties();

    @InjectMocks
    private TmdbImportServiceImpl tmdbImportService;

    private TmdbGenreDto actionTmdbGenre;
    private TmdbGenreDto comedyTmdbGenre;
    private Genre actionGenre;
    private TmdbMovieDetailDto sampleTmdbMovie;

    @BeforeEach
    void setUp() {
        // TMDB Genre DTOs
        actionTmdbGenre = new TmdbGenreDto();
        actionTmdbGenre.setId(28);
        actionTmdbGenre.setName("Action");

        comedyTmdbGenre = new TmdbGenreDto();
        comedyTmdbGenre.setId(35);
        comedyTmdbGenre.setName("Comedy");

        // Existing CineBook Genre
        actionGenre = new Genre();
        actionGenre.setId("genre-uuid-action");
        actionGenre.setTmdbId(28L);
        actionGenre.setName("Action");

        // Sample TMDB Movie Detail
        sampleTmdbMovie = new TmdbMovieDetailDto();
        sampleTmdbMovie.setId(550L);
        sampleTmdbMovie.setTitle("Fight Club");
        sampleTmdbMovie.setOriginalTitle("Fight Club");
        sampleTmdbMovie.setOverview("An insomniac office worker and a devil-may-care soapmaker form an underground fight club.");
        sampleTmdbMovie.setRuntime(139);
        sampleTmdbMovie.setReleaseDate("1999-10-15");
        sampleTmdbMovie.setOriginalLanguage("en");
        sampleTmdbMovie.setPosterPath("/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg");
        sampleTmdbMovie.setGenres(List.of(actionTmdbGenre));

        TmdbMovieDetailDto.TmdbCreditsDto credits = new TmdbMovieDetailDto.TmdbCreditsDto();
        TmdbMovieDetailDto.TmdbCrewDto director = new TmdbMovieDetailDto.TmdbCrewDto();
        director.setName("David Fincher");
        director.setJob("Director");
        TmdbMovieDetailDto.TmdbCastDto cast1 = new TmdbMovieDetailDto.TmdbCastDto();
        cast1.setName("Brad Pitt");
        cast1.setOrder(0);
        TmdbMovieDetailDto.TmdbCastDto cast2 = new TmdbMovieDetailDto.TmdbCastDto();
        cast2.setName("Edward Norton");
        cast2.setOrder(1);
        credits.setCrew(List.of(director));
        credits.setCast(List.of(cast1, cast2));
        sampleTmdbMovie.setCredits(credits);

        TmdbMovieDetailDto.TmdbVideosResultDto videos = new TmdbMovieDetailDto.TmdbVideosResultDto();
        TmdbMovieDetailDto.TmdbVideoDto trailer = new TmdbMovieDetailDto.TmdbVideoDto();
        trailer.setSite("YouTube");
        trailer.setType("Trailer");
        trailer.setKey("SUXWAEX2jlg");
        trailer.setOfficial(true);
        videos.setResults(List.of(trailer));
        sampleTmdbMovie.setVideos(videos);
    }

    // =========================================================
    //  Genre Sync Tests
    // =========================================================

    @Test
    void syncGenres_CreateNewGenres_Success() {
        TmdbGenreListResponse tmdbResponse = new TmdbGenreListResponse();
        tmdbResponse.setGenres(List.of(actionTmdbGenre, comedyTmdbGenre));

        when(tmdbClient.getMovieGenres(anyString())).thenReturn(tmdbResponse);
        when(genreRepository.findByTmdbId(28L)).thenReturn(Optional.empty());
        when(genreRepository.findByTmdbId(35L)).thenReturn(Optional.empty());
        when(genreRepository.save(any(Genre.class))).thenAnswer(inv -> {
            Genre g = inv.getArgument(0);
            g.setId("new-uuid-" + g.getTmdbId());
            return g;
        });

        TmdbGenreSyncResponse result = tmdbImportService.syncGenres();

        assertNotNull(result);
        assertEquals(2, result.getCreated());
        assertEquals(0, result.getUpdated());
        assertEquals(0, result.getUnchanged());
        assertEquals(2, result.getTotal());
        verify(genreRepository, times(2)).save(any(Genre.class));
    }

    @Test
    void syncGenres_UpdateChangedName() {
        Genre existingGenre = new Genre();
        existingGenre.setId("genre-28");
        existingGenre.setTmdbId(28L);
        existingGenre.setName("Old Action Name");

        TmdbGenreListResponse tmdbResponse = new TmdbGenreListResponse();
        tmdbResponse.setGenres(List.of(actionTmdbGenre)); // TMDB says "Action"

        when(tmdbClient.getMovieGenres(anyString())).thenReturn(tmdbResponse);
        when(genreRepository.findByTmdbId(28L)).thenReturn(Optional.of(existingGenre));
        when(genreRepository.save(any(Genre.class))).thenAnswer(inv -> inv.getArgument(0));

        TmdbGenreSyncResponse result = tmdbImportService.syncGenres();

        assertEquals(0, result.getCreated());
        assertEquals(1, result.getUpdated());
        assertEquals(0, result.getUnchanged());
        verify(genreRepository).save(existingGenre);
        assertEquals("Action", existingGenre.getName());
    }

    @Test
    void syncGenres_Unchanged_WhenNameSame() {
        TmdbGenreListResponse tmdbResponse = new TmdbGenreListResponse();
        tmdbResponse.setGenres(List.of(actionTmdbGenre));

        when(tmdbClient.getMovieGenres(anyString())).thenReturn(tmdbResponse);
        when(genreRepository.findByTmdbId(28L)).thenReturn(Optional.of(actionGenre)); // same name

        TmdbGenreSyncResponse result = tmdbImportService.syncGenres();

        assertEquals(0, result.getCreated());
        assertEquals(0, result.getUpdated());
        assertEquals(1, result.getUnchanged());
        verify(genreRepository, never()).save(any());
    }

    @Test
    void syncGenres_NoDuplicate_WhenRunTwice() {
        TmdbGenreListResponse tmdbResponse = new TmdbGenreListResponse();
        tmdbResponse.setGenres(List.of(actionTmdbGenre));

        // First run: not found, creates
        when(tmdbClient.getMovieGenres(anyString())).thenReturn(tmdbResponse);
        when(genreRepository.findByTmdbId(28L)).thenReturn(Optional.empty())
                .thenReturn(Optional.of(actionGenre)); // second run finds it
        when(genreRepository.save(any(Genre.class))).thenAnswer(inv -> {
            Genre g = inv.getArgument(0);
            g.setId("genre-28");
            return g;
        });

        TmdbGenreSyncResponse first = tmdbImportService.syncGenres();
        TmdbGenreSyncResponse second = tmdbImportService.syncGenres();

        assertEquals(1, first.getCreated());
        assertEquals(0, second.getCreated()); // no new creation on second run
        // Only one save (first run)
        verify(genreRepository, times(1)).save(any(Genre.class));
    }

    @Test
    void syncGenres_EmptyTmdbResponse_ReturnsZeroCounts() {
        TmdbGenreListResponse emptyResponse = new TmdbGenreListResponse();
        emptyResponse.setGenres(List.of());

        when(tmdbClient.getMovieGenres(anyString())).thenReturn(emptyResponse);

        TmdbGenreSyncResponse result = tmdbImportService.syncGenres();

        assertEquals(0, result.getTotal());
        verify(genreRepository, never()).save(any());
    }

    // =========================================================
    //  Movie Import Tests
    // =========================================================

    @Test
    void importMovie_CreateNew_Success() {
        when(tmdbClient.getMovieDetail(550L, "en-US")).thenReturn(sampleTmdbMovie);
        when(genreRepository.findAll()).thenReturn(List.of(actionGenre));
        when(movieRepository.findByTmdbId(550L)).thenReturn(Optional.empty());
        when(movieRepository.save(any(Movie.class))).thenAnswer(inv -> {
            Movie m = inv.getArgument(0);
            if (m.getId() == null) m.setId("movie-uuid-new");
            return m;
        });

        TmdbMovieImportResponse result = tmdbImportService.importMovie(550L);

        assertNotNull(result);
        assertEquals("CREATED", result.getAction());
        assertEquals(550L, result.getTmdbId());
        assertEquals("Fight Club", result.getTitle());
        assertNotNull(result.getMovieId());
        verify(movieRepository).save(any(Movie.class));
    }

    @Test
    void importMovie_UpdateExisting_PreservesStatus() {
        Movie existingMovie = new Movie();
        existingMovie.setId("existing-uuid");
        existingMovie.setTmdbId(550L);
        existingMovie.setTitle("Old Title");
        existingMovie.setStatus(MovieStatus.HIDDEN); // admin set this
        existingMovie.setMovieGenres(new HashSet<>());

        when(tmdbClient.getMovieDetail(550L, "en-US")).thenReturn(sampleTmdbMovie);
        when(genreRepository.findAll()).thenReturn(List.of(actionGenre));
        when(movieRepository.findByTmdbId(550L)).thenReturn(Optional.of(existingMovie));
        when(movieRepository.save(any(Movie.class))).thenAnswer(inv -> inv.getArgument(0));

        TmdbMovieImportResponse result = tmdbImportService.importMovie(550L);

        assertEquals("UPDATED", result.getAction());
        assertEquals("existing-uuid", result.getMovieId()); // UUID preserved
        assertEquals(MovieStatus.HIDDEN, result.getStatus()); // status NOT changed
        assertEquals("Fight Club", result.getTitle()); // TMDB data applied
    }

    @Test
    void importMovie_NoDuplicate_WhenCalledTwice() {
        Movie existingMovie = new Movie();
        existingMovie.setId("existing-uuid");
        existingMovie.setTmdbId(550L);
        existingMovie.setTitle("Fight Club");
        existingMovie.setStatus(MovieStatus.NOW_SHOWING);
        existingMovie.setMovieGenres(new HashSet<>());

        when(tmdbClient.getMovieDetail(550L, "en-US")).thenReturn(sampleTmdbMovie);
        when(genreRepository.findAll()).thenReturn(List.of(actionGenre));
        when(movieRepository.findByTmdbId(550L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingMovie));
        when(movieRepository.save(any(Movie.class))).thenAnswer(inv -> {
            Movie m = inv.getArgument(0);
            if (m.getId() == null) m.setId("existing-uuid");
            return m;
        });

        TmdbMovieImportResponse first = tmdbImportService.importMovie(550L);
        TmdbMovieImportResponse second = tmdbImportService.importMovie(550L);

        assertEquals("CREATED", first.getAction());
        assertEquals("UPDATED", second.getAction());
        assertEquals(first.getMovieId(), second.getMovieId()); // same UUID
        verify(movieRepository, times(2)).save(any(Movie.class)); // save called each time
    }

    @Test
    void importMovie_GenreMapping_UsesExistingGenreByTmdbId() {
        when(tmdbClient.getMovieDetail(550L, "en-US")).thenReturn(sampleTmdbMovie);
        when(genreRepository.findAll()).thenReturn(List.of(actionGenre)); // already exists
        when(movieRepository.findByTmdbId(550L)).thenReturn(Optional.empty());
        when(movieRepository.save(any(Movie.class))).thenAnswer(inv -> {
            Movie m = inv.getArgument(0);
            if (m.getId() == null) m.setId("movie-new");
            return m;
        });

        tmdbImportService.importMovie(550L);

        // genreRepository.save should NOT be called for existing genre
        verify(genreRepository, never()).save(any());
    }

    @Test
    void importMovie_AutoCreatesGenre_WhenNotInDb() {
        Genre newlyCreatedGenre = new Genre();
        newlyCreatedGenre.setId("new-genre-uuid");
        newlyCreatedGenre.setTmdbId(28L);
        newlyCreatedGenre.setName("Action");

        when(tmdbClient.getMovieDetail(550L, "en-US")).thenReturn(sampleTmdbMovie);
        when(genreRepository.findAll()).thenReturn(List.of()); // no genres in DB yet
        when(genreRepository.save(any(Genre.class))).thenAnswer(inv -> {
            Genre g = inv.getArgument(0);
            g.setId("new-genre-uuid");
            return g;
        });
        when(movieRepository.findByTmdbId(550L)).thenReturn(Optional.empty());
        when(movieRepository.save(any(Movie.class))).thenAnswer(inv -> {
            Movie m = inv.getArgument(0);
            if (m.getId() == null) m.setId("movie-new");
            return m;
        });

        TmdbMovieImportResponse result = tmdbImportService.importMovie(550L);

        assertNotNull(result);
        // genre was auto-created
        verify(genreRepository, atLeastOnce()).save(any(Genre.class));
    }

    @Test
    void importMovie_MissingTrailer_DoesNotFail() {
        sampleTmdbMovie.setVideos(null); // no videos

        when(tmdbClient.getMovieDetail(550L, "en-US")).thenReturn(sampleTmdbMovie);
        when(genreRepository.findAll()).thenReturn(List.of(actionGenre));
        when(movieRepository.findByTmdbId(550L)).thenReturn(Optional.empty());
        when(movieRepository.save(any(Movie.class))).thenAnswer(inv -> {
            Movie m = inv.getArgument(0);
            if (m.getId() == null) m.setId("movie-new");
            return m;
        });

        TmdbMovieImportResponse result = tmdbImportService.importMovie(550L);

        assertNotNull(result);
        assertNull(result.getTrailerUrl()); // null is OK
    }

    @Test
    void importMovie_MissingCredits_DoesNotFail() {
        sampleTmdbMovie.setCredits(null); // no credits

        when(tmdbClient.getMovieDetail(550L, "en-US")).thenReturn(sampleTmdbMovie);
        when(genreRepository.findAll()).thenReturn(List.of(actionGenre));
        when(movieRepository.findByTmdbId(550L)).thenReturn(Optional.empty());
        when(movieRepository.save(any(Movie.class))).thenAnswer(inv -> {
            Movie m = inv.getArgument(0);
            if (m.getId() == null) m.setId("movie-new");
            return m;
        });

        // Should not throw
        assertDoesNotThrow(() -> tmdbImportService.importMovie(550L));
    }

    @Test
    void importMovie_MissingTitle_ThrowsBadRequest() {
        sampleTmdbMovie.setTitle(null);

        when(tmdbClient.getMovieDetail(550L, "en-US")).thenReturn(sampleTmdbMovie);

        assertThrows(BadRequestException.class, () -> tmdbImportService.importMovie(550L));
        verify(movieRepository, never()).save(any());
    }

    @Test
    void importMovie_ImageUrlBuilding() {
        when(tmdbClient.getMovieDetail(550L, "en-US")).thenReturn(sampleTmdbMovie);
        when(genreRepository.findAll()).thenReturn(List.of(actionGenre));
        when(movieRepository.findByTmdbId(550L)).thenReturn(Optional.empty());
        when(movieRepository.save(any(Movie.class))).thenAnswer(inv -> {
            Movie m = inv.getArgument(0);
            if (m.getId() == null) m.setId("movie-new");
            return m;
        });

        TmdbMovieImportResponse result = tmdbImportService.importMovie(550L);

        // posterPath was "/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg" → should become full URL
        assertNotNull(result.getPosterUrl());
        assertTrue(result.getPosterUrl().contains("image.tmdb.org"));
        assertTrue(result.getPosterUrl().contains("pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg"));
    }

    @Test
    void importMovie_TrailerUrl_YouTubeOfficialPreferred() {
        TmdbMovieDetailDto.TmdbVideosResultDto videos = new TmdbMovieDetailDto.TmdbVideosResultDto();
        TmdbMovieDetailDto.TmdbVideoDto unofficialTrailer = new TmdbMovieDetailDto.TmdbVideoDto();
        unofficialTrailer.setSite("YouTube");
        unofficialTrailer.setType("Trailer");
        unofficialTrailer.setKey("unofficial-key");
        unofficialTrailer.setOfficial(false);

        TmdbMovieDetailDto.TmdbVideoDto officialTrailer = new TmdbMovieDetailDto.TmdbVideoDto();
        officialTrailer.setSite("YouTube");
        officialTrailer.setType("Trailer");
        officialTrailer.setKey("official-key");
        officialTrailer.setOfficial(true);

        videos.setResults(List.of(unofficialTrailer, officialTrailer));
        sampleTmdbMovie.setVideos(videos);

        when(tmdbClient.getMovieDetail(550L, "en-US")).thenReturn(sampleTmdbMovie);
        when(genreRepository.findAll()).thenReturn(List.of(actionGenre));
        when(movieRepository.findByTmdbId(550L)).thenReturn(Optional.empty());
        when(movieRepository.save(any(Movie.class))).thenAnswer(inv -> {
            Movie m = inv.getArgument(0);
            if (m.getId() == null) m.setId("movie-new");
            return m;
        });

        TmdbMovieImportResponse result = tmdbImportService.importMovie(550L);

        assertNotNull(result.getTrailerUrl());
        assertTrue(result.getTrailerUrl().contains("official-key")); // official preferred
    }

    @Test
    void importMovie_SoftDeletedMovie_IsUpdatedButNotRestored() {
        Movie deletedMovie = new Movie();
        deletedMovie.setId("deleted-movie-uuid");
        deletedMovie.setTmdbId(550L);
        deletedMovie.setStatus(MovieStatus.HIDDEN);
        deletedMovie.setMovieGenres(new HashSet<>());
        // deletedAt would be set in real scenario — we check status is not changed

        when(tmdbClient.getMovieDetail(550L, "en-US")).thenReturn(sampleTmdbMovie);
        when(genreRepository.findAll()).thenReturn(List.of(actionGenre));
        when(movieRepository.findByTmdbId(550L)).thenReturn(Optional.of(deletedMovie));
        when(movieRepository.save(any(Movie.class))).thenAnswer(inv -> inv.getArgument(0));

        TmdbMovieImportResponse result = tmdbImportService.importMovie(550L);

        assertEquals("UPDATED", result.getAction());
        assertEquals(MovieStatus.HIDDEN, result.getStatus()); // status preserved
        assertEquals("deleted-movie-uuid", result.getMovieId()); // UUID preserved
        assertEquals("Fight Club", result.getTitle()); // TMDB data applied
    }

    @Test
    void importMovie_MovieGenreSync_RemovesOldAddsNew() {
        Genre comedyGenre = new Genre();
        comedyGenre.setId("genre-comedy");
        comedyGenre.setTmdbId(35L);
        comedyGenre.setName("Comedy");

        // Existing movie has Comedy genre
        Movie existingMovie = new Movie();
        existingMovie.setId("existing-uuid");
        existingMovie.setTmdbId(550L);
        existingMovie.setStatus(MovieStatus.NOW_SHOWING);
        HashSet<MovieGenre> existingGenres = new HashSet<>();
        MovieGenre oldMg = new MovieGenre();
        oldMg.setId(new MovieGenreId("existing-uuid", "genre-comedy"));
        oldMg.setMovie(existingMovie);
        oldMg.setGenre(comedyGenre);
        existingGenres.add(oldMg);
        existingMovie.setMovieGenres(existingGenres);

        // TMDB now has Action (not Comedy)
        sampleTmdbMovie.setGenres(List.of(actionTmdbGenre));

        when(tmdbClient.getMovieDetail(550L, "en-US")).thenReturn(sampleTmdbMovie);
        when(genreRepository.findAll()).thenReturn(List.of(actionGenre, comedyGenre));
        when(movieRepository.findByTmdbId(550L)).thenReturn(Optional.of(existingMovie));
        when(movieRepository.save(any(Movie.class))).thenAnswer(inv -> inv.getArgument(0));

        TmdbMovieImportResponse result = tmdbImportService.importMovie(550L);

        // Comedy should be removed, Action added
        assertEquals(1, result.getGenres().size());
        assertTrue(result.getGenres().contains("Action"));
        assertFalse(result.getGenres().contains("Comedy"));
    }
}
