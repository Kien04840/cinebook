package com.cinebook.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends AppException {

    public ForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }

    public ForbiddenException(String message, String code) {
        super(message, HttpStatus.FORBIDDEN, code);
    }

    public ForbiddenException(String message, ErrorCode code) {
        super(message, HttpStatus.FORBIDDEN, code);
    }
}

