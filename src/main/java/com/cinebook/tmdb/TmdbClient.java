package com.cinebook.tmdb;

import com.cinebook.dto.tmdb.TmdbGenreListResponse;
import com.cinebook.dto.tmdb.TmdbMovieDetailDto;

/**
 * Abstraction layer for TMDB REST API calls.
 * Implementation details (HTTP client, retry, etc.) are isolated here,
 * allowing replacement without touching business logic.
 */
public interface TmdbClient {

    /**
     * Fetches the list of official TMDB movie genres.
     *
     * @param language TMDB language code, e.g. "en-US"
     * @return TMDB genre list response
     * @throws com.cinebook.exception.TmdbAuthException    if API key is invalid
     * @throws com.cinebook.exception.TmdbServiceException if TMDB is unreachable or returns 5xx
     */
    TmdbGenreListResponse getMovieGenres(String language);

    /**
     * Fetches full movie detail including credits, videos, and release_dates.
     *
     * @param tmdbId   TMDB movie id
     * @param language TMDB language code
     * @return detailed movie DTO
     * @throws com.cinebook.exception.TmdbResourceNotFoundException if movie does not exist on TMDB
     * @throws com.cinebook.exception.TmdbAuthException             if API key is invalid
     * @throws com.cinebook.exception.TmdbServiceException          if TMDB is unreachable or returns 5xx
     */
    TmdbMovieDetailDto getMovieDetail(Long tmdbId, String language);
}
