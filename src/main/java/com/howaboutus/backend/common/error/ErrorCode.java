package com.howaboutus.backend.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 BAD REQUEST
    INVALID_PLACE_QUERY(HttpStatus.BAD_REQUEST, "검색어는 공백일 수 없습니다"),

    // 502 BAD GATEWAY
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 API 호출 중 오류가 발생했습니다");

    private final HttpStatus status;
    private final String message;
}
