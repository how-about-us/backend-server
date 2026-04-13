package com.howaboutus.backend.common.error;

public class ExternalApiException extends CustomException {

    public ExternalApiException(Throwable cause) {
        super(ErrorCode.EXTERNAL_API_ERROR);
        initCause(cause);
    }
}
