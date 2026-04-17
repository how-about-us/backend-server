package com.howaboutus.backend.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 401 UNAUTHORIZED
    GOOGLE_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "Google 인증에 실패했습니다"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 존재하지 않습니다"),
    REFRESH_TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "토큰 재사용이 감지되었습니다"),

    // 502 BAD GATEWAY
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 API 호출 중 오류가 발생했습니다");

    private final HttpStatus status;
    private final String message;
}
