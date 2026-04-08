package com.howaboutus.backend.common.client.google.exception;

public class GoogleApiClientException extends RuntimeException {

    public GoogleApiClientException(String message) {
        super(message);
    }

    public GoogleApiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
