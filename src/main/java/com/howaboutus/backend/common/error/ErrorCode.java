package com.howaboutus.backend.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_PLACE_QUERY(HttpStatus.BAD_REQUEST, "INVALID_PLACE_QUERY", "query must not be blank");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
