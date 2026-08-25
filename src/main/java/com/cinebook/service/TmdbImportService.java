package com.cinebook.service;

import com.cinebook.dto.response.TmdbGenreSyncResponse;
import com.cinebook.dto.response.TmdbMovieImportResponse;

/**
 * Service for importing/synchronizing data from TMDB into CineBook.
 * TMDB is treated as an external data source; CineBook MySQL remains the runtime source of truth.
 */
public interface TmdbImportService {

    /**
     * Synchronizes TMDB genre list with CineBook genres.
     * Creates new genres and updates changed names; does NOT delete existing CineBook genres.
     *
     * @return sync result counts (created, updated, unchanged, total)
     */
    TmdbGenreSyncResponse syncGenres();

    /**
     * Imports or updates a movie from TMDB by its TMDB id.
     *
     * <p>If no movie with the given tmdbId exists, creates a new Movie with a fresh UUID.
     * If a movie already exists, updates TMDB-sourced fields (title, overview, etc.)
     * but preserves CineBook lifecycle fields (status, deletedAt, createdAt, id).</p>
     *
     * @param tmdbId TMDB movie id
     * @return import result with CineBook movie id and action taken
     * @throws com.cinebook.exception.TmdbResourceNotFoundException if the movie does not exist on TMDB
     * @throws com.cinebook.exception.BadRequestException           if TMDB data is missing required fields
     */
    TmdbMovieImportResponse importMovie(Long tmdbId);
}
