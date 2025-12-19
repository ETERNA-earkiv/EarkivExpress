package com.example.restservice.exception;

public final class UploadFailureException extends RuntimeException {
    public UploadFailureException(Throwable cause) {
        super("Upload failed", cause);
      System.out.println("Upload failure");
    }
}
