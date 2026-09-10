package com.cinebook.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends AppException {

    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }

    public UnauthorizedException(String message, String code) {
        super(message, HttpStatus.UNAUTHORIZED, code);
    }

    public UnauthorizedException(String message, ErrorCode code) {
        super(message, HttpStatus.UNAUTHORIZED, code);
    }
}

