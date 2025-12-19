package com.example.restservice.exception;

public final class BadMultipartException extends RuntimeException {
    public BadMultipartException(String message) {
        super(message);
    }
}
