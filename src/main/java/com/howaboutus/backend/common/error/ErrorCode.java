package com.howaboutus.backend.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 BAD REQUEST
    INVALID_PLACE_QUERY(HttpStatus.BAD_REQUEST, "검색어는 공백일 수 없습니다"),
    INVALID_LOCATION_PARAMS(HttpStatus.BAD_REQUEST, "latitude와 longitude는 함께 제공해야 합니다"),

    // 401 UNAUTHORIZED
    GOOGLE_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "Google 인증에 실패했습니다"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),

    // 502 BAD GATEWAY
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 API 호출 중 오류가 발생했습니다");

    private final HttpStatus status;
    private final String message;
}
