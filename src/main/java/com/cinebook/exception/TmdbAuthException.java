package com.cinebook.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when TMDB returns 401 or 403.
 * Maps to 503 since this is a configuration issue.
 */
public class TmdbAuthException extends TmdbApiException {

    public TmdbAuthException(String message) {
        super("TMDB authentication failed (check TMDB_API_KEY configuration): " + message,
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}