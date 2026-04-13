package com.howaboutus.backend.common.error;

import org.springframework.http.HttpStatus;

public record ApiErrorResponse(String code, String message) {

    /**
     * 생성자 대신 of 메서드를 이용해 주세요.
     *
     * @author Minhyung Kim
     */
    public static ApiErrorResponse of(ErrorCode errorCode) {
        return new ApiErrorResponse(errorCode.name(), errorCode.getMessage());
    }

    public static ApiErrorResponse of(HttpStatus status, String message) {
        return new ApiErrorResponse(status.name(), message);
    }
}
