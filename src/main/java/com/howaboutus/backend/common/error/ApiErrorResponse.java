package com.howaboutus.backend.common.error;

import org.springframework.http.HttpStatus;

public record ApiErrorResponse(HttpStatus status, String message) {

    /**
     * 생성자 대신 of 메서드를 이용해 주세요.
     *
     * @author Minhyung Kim
     */
    public static ApiErrorResponse of(ErrorCode errorCode) {
        return new ApiErrorResponse(errorCode.getStatus(), errorCode.getMessage());
    }

    public static ApiErrorResponse of(HttpStatus code, String message) {
        return new ApiErrorResponse(code, message);
    }
}
