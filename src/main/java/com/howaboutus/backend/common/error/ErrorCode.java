package com.howaboutus.backend.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 BAD REQUEST
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "시작일이 종료일보다 늦을 수 없습니다"),
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "size는 1 이상이어야 합니다"),

    // 401 UNAUTHORIZED
    GOOGLE_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "Google 인증에 실패했습니다"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),
    ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "액세스 토큰이 만료되었습니다"),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 존재하지 않습니다"),
    REFRESH_TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "토큰 재사용이 감지되었습니다"),

    // 400 BAD REQUEST
    SCHEDULE_DATE_MISMATCH(HttpStatus.BAD_REQUEST, "여행 날짜와 일차 정보가 일치하지 않습니다"),
    INVALID_ORDER_INDEX(HttpStatus.BAD_REQUEST, "유효하지 않은 순서 인덱스입니다"),

    // 403 FORBIDDEN
    NOT_ROOM_MEMBER(HttpStatus.FORBIDDEN, "방의 멤버가 아닙니다"),
    NOT_ROOM_HOST(HttpStatus.FORBIDDEN, "호스트 권한이 필요합니다"),

    // 404 NOT FOUND
    JOIN_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 입장 요청입니다"),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 방입니다"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"),
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
