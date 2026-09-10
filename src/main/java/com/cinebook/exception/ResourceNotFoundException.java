package com.cinebook.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }

    public ResourceNotFoundException(String message, String code) {
        super(message, HttpStatus.NOT_FOUND, code);
    }

    public ResourceNotFoundException(String message, ErrorCode code) {
        super(message, HttpStatus.NOT_FOUND, code);
    }
}

