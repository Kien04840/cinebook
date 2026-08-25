package com.cinebook.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when TMDB returns 404.
 */
public class TmdbResourceNotFoundException extends TmdbApiException {

    public TmdbResourceNotFoundException(Long tmdbId) {
        super("TMDB resource not found with id: " + tmdbId, HttpStatus.NOT_FOUND);
    }

    public TmdbResourceNotFoundException(String resource) {
        super("TMDB resource not found: " + resource, HttpStatus.NOT_FOUND);
    }
}