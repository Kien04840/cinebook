package com.cinebook.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AppException(String message, HttpStatus status) {
        this(message, status, (String) null);
    }

    public AppException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public AppException(String message, HttpStatus status, ErrorCode code) {
        this(message, status, code != null ? code.name() : null);
    }

    public AppException(String message, Throwable cause, HttpStatus status) {
        this(message, cause, status, (String) null);
    }

    public AppException(String message, Throwable cause, HttpStatus status, String code) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public AppException(String message, Throwable cause, HttpStatus status, ErrorCode code) {
        this(message, cause, status, code != null ? code.name() : null);
    }
}

