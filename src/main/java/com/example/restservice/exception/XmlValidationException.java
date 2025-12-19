package com.example.restservice.exception;

public final class XmlValidationException extends RuntimeException {
    public XmlValidationException(Throwable cause) {
        super("XML validation failed", cause);
    }
}
