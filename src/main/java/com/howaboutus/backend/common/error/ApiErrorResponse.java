package com.howaboutus.backend.common.error;

public record ApiErrorResponse(String code, String message) {

    /**
     * 생성자 대신 of 메서드를 이용해 주세요.
     *
     * @author Minhyung Kim
     */
    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message);
    }
}
