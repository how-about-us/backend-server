package com.howaboutus.backend.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 BAD REQUEST
    INVALID_PLACE_QUERY(HttpStatus.BAD_REQUEST, "검색어는 공백일 수 없습니다");

    private final HttpStatus status;
    private final String message;
}
