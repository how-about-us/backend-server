---
name: api-designer
description: >
  Use when designing or modifying REST API endpoints, WebSocket/STOMP message flows, or HTTP response
  formats in this Spring Boot project. Covers response format conventions, HTTP status code usage,
  WebSocket security configuration, and rate limiting considerations.
  Make sure to use this skill whenever the user asks to add an API, design an endpoint, define request/response
  format, work on WebSocket or STOMP messaging, or review API structure.
---

# API Designer

## 역할 (Role)

REST API와 WebSocket/STOMP 실시간 통신을 일관성 있게 설계한다. **클라이언트가 예측 가능하게 사용할 수 있는 API**와 **운영 환경에서 안전하게 동작하는 WebSocket 설정**을 만드는 것이 목표다.

---

## 원칙 (Principles)

1. **응답 포맷을 프로젝트 전체에서 통일한다** — 성공/실패 응답 구조가 엔드포인트마다 다르면 클라이언트가 매번 다르게 처리해야 한다. 전역 응답 래퍼와 에러 응답 형식을 정하고 모든 엔드포인트가 따르게 한다.

2. **HTTP 상태 코드는 의미에 맞게 사용한다** — 모든 응답을 `200 OK`에 에러 코드를 담아 반환하면 HTTP 레이어의 의미가 사라져 클라이언트 분기 처리가 복잡해진다. 생성은 `201`, 인증 실패는 `401`, 권한 없음은 `403`, 리소스 없음은 `404`로 구분한다.

3. **WebSocket 엔드포인트는 Security 설정에서 명시적으로 허용한다** — Spring Security의 기본 설정은 WebSocket 핸드셰이크 경로를 차단한다. STOMP 엔드포인트와 메시지 브로커 경로를 `SecurityFilterChain`에서 별도 허용해야 한다.

4. **API 명세서와 구현을 동기화한다** — 엔드포인트를 추가/변경하면 반드시 `docs/ai/api-spec.md`를 갱신한다. 문서와 코드의 불일치는 프론트엔드 개발 블로커가 된다.

5. **rate limiting은 설계 시점에 고려한다** — 인증 엔드포인트, 공개 검색 API처럼 남용 가능성이 있는 엔드포인트는 구현 전에 rate limit 적용 여부를 결정한다.

---

## 작업 전 체크리스트

- [ ] 기존 API 스펙을 `docs/ai/api-spec.md`에서 확인한다.
- [ ] 유사한 엔드포인트의 응답 포맷 패턴을 확인한다.
- [ ] 인증이 필요한 엔드포인트인지, 권한 레벨은 무엇인지 결정한다.
- [ ] WebSocket 관련이면 Security 설정 파일을 확인한다.

## 작업 후 체크리스트

- [ ] `docs/ai/api-spec.md`를 갱신한다.
- [ ] 응답 포맷이 프로젝트 표준을 따르는지 확인한다.
- [ ] HTTP 상태 코드가 의미에 맞게 사용되는지 확인한다.
- [ ] WebSocket 엔드포인트를 추가했으면 Security 허용 목록에 포함되어 있는지 확인한다.
- [ ] 새 엔드포인트에 `@Valid` 입력 검증이 있는지 확인한다.

---

## REST 응답 포맷

### 성공 응답

```json
// 단일 리소스
{
  "data": { ... }
}

// 목록 (페이지네이션 포함)
{
  "data": [ ... ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5
  }
}
```

### 에러 응답

```json
{
  "error": {
    "code": "MEETING_NOT_FOUND",
    "message": "미팅을 찾을 수 없습니다.",
    "details": null
  }
}
```

---

## HTTP 상태 코드 사용 원칙

| 상황 | 상태 코드 | 비고 |
|------|-----------|------|
| 조회 성공 | `200 OK` | |
| 생성 성공 | `201 Created` | `Location` 헤더 포함 권장 |
| 삭제/처리 성공 (응답 본문 없음) | `204 No Content` | |
| 입력 유효성 실패 | `400 Bad Request` | Bean Validation 오류 |
| 인증 안 됨 (토큰 없음/만료) | `401 Unauthorized` | |
| 권한 없음 (로그인했지만 접근 불가) | `403 Forbidden` | |
| 리소스 없음 | `404 Not Found` | |
| 비즈니스 규칙 위반 (정원 초과 등) | `409 Conflict` | 또는 `422 Unprocessable Entity` |
| 서버 에러 | `500 Internal Server Error` | 전역 예외 핸들러에서 처리 |

---

## WebSocket / STOMP 설계 규칙

### 엔드포인트 명명 규칙

```
WebSocket 연결 엔드포인트:  /ws
구독 경로 (서버→클라이언트): /topic/{resource}
메시지 발행 경로 (클라이언트→서버): /app/{action}

예시:
/topic/meetings/{meetingId}/chat    — 채팅 메시지 브로드캐스트
/topic/meetings/{meetingId}/status  — 미팅 상태 변경 알림
/app/meetings/{meetingId}/chat      — 채팅 메시지 전송
```

### Spring Security 설정 (필수)

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/ws/**").permitAll()  // WebSocket 핸드셰이크 허용
            .requestMatchers("/app/**").permitAll() // STOMP 메시지 경로 허용
            .anyRequest().authenticated()
        );
    return http.build();
}
```

WebSocket 엔드포인트를 Security에서 허용하지 않으면 핸드셰이크 단계에서 `403`이 발생한다.

### STOMP 메시지 응답 포맷

```json
// 브로드캐스트 메시지 (서버 → 클라이언트)
{
  "type": "CHAT_MESSAGE",
  "payload": {
    "senderId": 1,
    "content": "안녕하세요",
    "sentAt": "2024-01-01T12:00:00"
  }
}
```

---

## Rate Limiting 고려 사항

아래 엔드포인트는 구현 전에 rate limit 적용 여부를 명시적으로 결정한다:

| 엔드포인트 유형 | 남용 가능성 | 권고 |
|---|---|---|
| 인증 (로그인, 토큰 재발급) | 높음 | IP 기반 rate limit 적용 |
| 위치 기반 검색 | 중간 | 사용자 기반 rate limit 검토 |
| 공개 조회 API | 낮음 | 모니터링 우선 |
| 실시간 메시지 발행 | 중간 | 연결당 메시지 빈도 제한 검토 |

---

## 금지 사항 (Never Do)

| 금지 행동 | 이유 |
|---|---|
| 모든 응답에 `200 OK` 사용 | HTTP 의미 상실, 클라이언트 분기 처리 복잡화 |
| Entity를 API 응답으로 직접 노출 | 내부 모델 유출, 불필요한 필드 노출 |
| WebSocket 경로 Security 허용 누락 | 핸드셰이크 `403` 오류 |
| API 변경 후 `docs/ai/api-spec.md` 미갱신 | 프론트엔드와 명세 불일치 |
| 인증이 필요한 엔드포인트에 `@AuthenticationPrincipal` 없이 userId 직접 수신 | 위조 가능한 userId로 권한 우회 위험 |

---

## 참고 문서

| 문서 | 경로 | 읽는 시점 |
|------|------|-----------|
| API 명세서 | `docs/ai/api-spec.md` | 엔드포인트 설계 또는 STOMP 브로드캐스트 확인/갱신 시 |
| 기능 명세서 | `docs/ai/features.md` | API가 지원해야 할 기능 확인 시 |

---

## 이 프로젝트 특이사항

- **Spring Boot 4 / Spring 6** — `HttpMethod.resolve()` deprecated, `jakarta.*` 패키지 사용
- **WebSocket + STOMP** — Security 설정에서 `/ws/**` 경로 반드시 허용
- **OAuth2 인증** — `@AuthenticationPrincipal`로 인증된 사용자 정보 획득
