package com.cinebook.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when TMDB is unreachable, returns 5xx, or a network timeout occurs.
 */
public class TmdbServiceException extends TmdbApiException {

    public TmdbServiceException(String message) {
        super("TMDB service unavailable: " + message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public TmdbServiceException(String message, Throwable cause) {
        super("TMDB service unavailable: " + message, cause, HttpStatus.SERVICE_UNAVAILABLE);
    }
}