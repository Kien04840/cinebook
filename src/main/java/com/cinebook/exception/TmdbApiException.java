package com.cinebook.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for all TMDB API integration errors.
 */
public class TmdbApiException extends AppException {

    public TmdbApiException(String message, HttpStatus status) {
        super(message, status);
    }

    public TmdbApiException(String message, Throwable cause, HttpStatus status) {
        super(message, cause, status);
    }
}