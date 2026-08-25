package com.cinebook.tmdb;

import com.cinebook.config.TmdbProperties;
import com.cinebook.dto.tmdb.TmdbGenreDto;
import com.cinebook.dto.tmdb.TmdbGenreListResponse;
import com.cinebook.dto.tmdb.TmdbMovieDetailDto;
import com.cinebook.exception.TmdbApiException;
import com.cinebook.exception.TmdbAuthException;
import com.cinebook.exception.TmdbResourceNotFoundException;
import com.cinebook.exception.TmdbServiceException;
import com.cinebook.tmdb.impl.TmdbApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for TmdbApiClient focusing on error-handling and exception mapping.
 *
 * TmdbApiClient builds RestClient internally using TmdbProperties, making it
 * difficult to intercept HTTP at the RestClient level in unit tests without
 * starting a real HTTP server. These tests therefore focus on:
 *  - Exception propagation from the client layer
 *  - Exception hierarchy correctness
 *  - TmdbProperties URL building
 */
class TmdbApiClientTest {

    private TmdbProperties buildTestProperties() {
        TmdbProperties props = new TmdbProperties();
        props.setApiKey("test-key");
        props.setBaseUrl("https://api.themoviedb.org/3");
        props.setLanguage("en-US");
        props.setImageBaseUrl("https://image.tmdb.org/t/p");
        props.setPosterSize("w500");
        props.setBackdropSize("original");
        return props;
    }

    // =========================================================
    //  Exception Hierarchy Tests
    // =========================================================

    @Test
    void tmdbResourceNotFoundException_IsSubclassOfTmdbApiException() {
        TmdbResourceNotFoundException ex = new TmdbResourceNotFoundException(550L);
        assertTrue(ex instanceof TmdbApiException);
        assertTrue(ex.getMessage().contains("550"));
    }

    @Test
    void tmdbAuthException_IsSubclassOfTmdbApiException() {
        TmdbAuthException ex = new TmdbAuthException("HTTP 401");
        assertTrue(ex instanceof TmdbApiException);
        assertTrue(ex.getMessage().contains("authentication failed"));
    }

    @Test
    void tmdbServiceException_IsSubclassOfTmdbApiException() {
        TmdbServiceException ex = new TmdbServiceException("Connection refused");
        assertTrue(ex instanceof TmdbApiException);
        assertTrue(ex.getMessage().contains("unavailable"));
    }

    @Test
    void tmdbServiceException_WithCause_PreservesCause() {
        ResourceAccessException cause = new ResourceAccessException("timeout");
        TmdbServiceException ex = new TmdbServiceException("timeout error", cause);
        assertEquals(cause, ex.getCause());
    }

    // =========================================================
    //  TmdbProperties URL Building Tests
    // =========================================================

    @Test
    void tmdbProperties_BuildPosterUrl_WithLeadingSlash() {
        TmdbProperties props = buildTestProperties();
        String url = props.buildPosterUrl("/poster.jpg");
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", url);
    }

    @Test
    void tmdbProperties_BuildPosterUrl_WithoutLeadingSlash() {
        TmdbProperties props = buildTestProperties();
        String url = props.buildPosterUrl("poster.jpg");
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", url);
    }

    @Test
    void tmdbProperties_BuildPosterUrl_NullPath_ReturnsNull() {
        TmdbProperties props = buildTestProperties();
        assertNull(props.buildPosterUrl(null));
    }

    @Test
    void tmdbProperties_BuildPosterUrl_BlankPath_ReturnsNull() {
        TmdbProperties props = buildTestProperties();
        assertNull(props.buildPosterUrl("  "));
    }

    @Test
    void tmdbProperties_BuildBackdropUrl_WithLeadingSlash() {
        TmdbProperties props = buildTestProperties();
        String url = props.buildBackdropUrl("/backdrop.jpg");
        assertEquals("https://image.tmdb.org/t/p/original/backdrop.jpg", url);
    }

    // =========================================================
    //  TmdbApiClient Construction Test
    // =========================================================

    @Test
    void tmdbApiClient_Constructs_WithValidProperties() {
        TmdbProperties props = buildTestProperties();
        // Should not throw during construction
        assertDoesNotThrow(() -> new TmdbApiClient(props));
    }

    @Test
    void tmdbResourceNotFoundException_StringConstructor() {
        TmdbResourceNotFoundException ex = new TmdbResourceNotFoundException("genres");
        assertTrue(ex.getMessage().contains("genres"));
    }
}