package com.cinebook.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }

    public ConflictException(String message, String code) {
        super(message, HttpStatus.CONFLICT, code);
    }

    public ConflictException(String message, ErrorCode code) {
        super(message, HttpStatus.CONFLICT, code);
    }
}

