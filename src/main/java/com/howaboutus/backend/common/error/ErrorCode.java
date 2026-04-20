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

    // 400 BAD REQUEST
    SCHEDULE_DATE_MISMATCH(HttpStatus.BAD_REQUEST, "여행 날짜와 일차 정보가 일치하지 않습니다"),

    // 404 NOT FOUND
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다"),
    BOOKMARK_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "북마크 카테고리를 찾을 수 없습니다"),
    BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "북마크를 찾을 수 없습니다"),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다"),
    SCHEDULE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "일정 항목을 찾을 수 없습니다"),

    // 409 CONFLICT
    BOOKMARK_CATEGORY_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 북마크 카테고리입니다"),
    BOOKMARK_CATEGORY_EMPTY(HttpStatus.CONFLICT, "북마크 카테고리가 없습니다"),
    BOOKMARK_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 보관함에 추가된 장소입니다"),
    SCHEDULE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 일정입니다"),
    SCHEDULE_CONFLICT(HttpStatus.CONFLICT, "일정이 동시에 변경되었습니다. 최신 상태를 확인한 뒤 다시 시도해 주세요"),

    // 502 BAD GATEWAY
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 API 호출 중 오류가 발생했습니다");

    private final HttpStatus status;
    private final String message;
}
