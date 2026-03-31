# API 명세서

> **초안 문서입니다.** DTO 필드는 아직 확정되지 않았으며 구현 과정에서 충분히 변경될 수 있습니다.
>
> 원본 Notion 명세: https://www.notion.so/332fd464a7b880759c49cec60bf47243

---

## 통신 방식

이 서비스는 두 가지 통신 채널을 혼용합니다.

| 채널 | 방향 | 용도 |
|------|------|------|
| **REST HTTP** | 클라이언트 → 서버 | 일반 CRUD, 조회 |
| **STOMP over WebSocket** | 클라이언트 → 서버 | 채팅 메시지 전송 |
| **STOMP over WebSocket** | 서버 → 클라이언트 | 실시간 브로드캐스트 (채팅, 일정 변경, 멤버 이벤트 등) |

> **주의:** 현재 Notion 명세에는 클라이언트 → 서버 방향만 기술되어 있습니다.
> 서버 → 클라이언트 STOMP 브로드캐스트 스펙은 이 문서 하단 **[STOMP 브로드캐스트](#stomp-브로드캐스트-예상안-서버--클라이언트)** 섹션에 별도 정리합니다.

---

## 공통 에러 코드

| HTTP 상태 | 코드 | 설명 |
|-----------|------|------|
| 400 | `BAD_REQUEST` | 잘못된 요청 파라미터 |
| 401 | `UNAUTHORIZED` | 인증 필요 (토큰 없음/만료) |
| 403 | `FORBIDDEN` | 권한 없음 (ADMIN 전용 기능 등) |
| 404 | `ENTITY_NOT_FOUND` | 리소스 없음 |
| 409 | `ALREADY_EXISTS` | 중복 리소스 (이미 가입된 방 등) |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 내부 오류 |
| 503 | `SERVICE_UNAVAILABLE` | 외부 서비스 장애 (Google API 등) |

---

## REST API

### 1. 인증 (Auth)

| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| `GET` | `/oauth2/authorization/google` | 구글 OAuth 로그인 시작 | 불필요 |
| `POST` | `/api/auth/refresh` | Access Token 재발급 | Refresh Token |
| `POST` | `/api/auth/logout` | 로그아웃 (토큰 무효화) | 필요 |
| `GET` | `/api/auth/me` | 내 정보 조회 | 필요 |

---

### 2. 여행 방 (Rooms)

| 메서드 | 경로 | 설명 | 권한 |
|--------|------|------|------|
| `POST` | `/api/rooms` | 방 생성 (invite_code 자동 발급) | 로그인 |
| `GET` | `/api/rooms` | 내 방 목록 조회 | 로그인 |
| `GET` | `/api/rooms/{roomId}` | 방 상세 조회 | 멤버 |
| `PATCH` | `/api/rooms/{roomId}` | 방 수정 | ADMIN |
| `DELETE` | `/api/rooms/{roomId}` | 방 삭제 | ADMIN |
| `POST` | `/api/rooms/{roomId}/invite-code` | 초대 코드 재발급 | ADMIN |
| `POST` | `/api/rooms/join` | 초대 코드로 방 입장 | 로그인 |

---

### 3. 멤버 관리 (Room Members)

| 메서드 | 경로 | 설명 | 권한 |
|--------|------|------|------|
| `GET` | `/api/rooms/{roomId}/members` | 방 멤버 목록 조회 (역할 + 접속 상태) | 멤버 |
| `DELETE` | `/api/rooms/{roomId}/members/{userId}` | 멤버 강퇴 | ADMIN |
| `DELETE` | `/api/rooms/{roomId}/members/me` | 방 나가기 | 멤버 |

> 현재 접속 중인 유저 목록은 멤버 목록 조회 응답에 `isOnline` 필드로 포함하거나 별도 엔드포인트로 분리 가능 (미결).

---

### 4. 장소 (Places)

| 메서드 | 경로 | 설명 | 권한 |
|--------|------|------|------|
| `GET` | `/api/places/search` | 장소 검색 (Google Places API + DB 캐싱) | 멤버 |
| `GET` | `/api/places/{placeId}` | 장소 상세 조회 | 멤버 |

---

### 5. 보관함 (Bookmarks)

| 메서드 | 경로 | 설명 | 권한 |
|--------|------|------|------|
| `POST` | `/api/rooms/{roomId}/bookmarks` | 보관함에 장소 추가 | 멤버 |
| `GET` | `/api/rooms/{roomId}/bookmarks` | 보관함 목록 조회 (카테고리 필터) | 멤버 |
| `DELETE` | `/api/rooms/{roomId}/bookmarks/{bookmarkId}` | 보관함 항목 삭제 | 멤버 |
| `PATCH` | `/api/rooms/{roomId}/bookmarks/{bookmarkId}/memo` | 보관함 메모 수정 | 멤버 |
| `PATCH` | `/api/rooms/{roomId}/bookmarks/{bookmarkId}/category` | 보관함 카테고리 변경 | 멤버 |

> 장소 채팅 공유는 채팅 메시지 전송(`POST /api/rooms/{roomId}/messages`)과 통합. `message_type: PLACE_SHARE`로 구분.

---

### 6. 일정 (Schedules)

#### 6-1. 일정 (일자 단위)

| 메서드 | 경로 | 설명 | 권한 |
|--------|------|------|------|
| `POST` | `/api/rooms/{roomId}/schedules` | 일정 생성 (N일차 + 날짜) | 멤버 |
| `GET` | `/api/rooms/{roomId}/schedules` | 전체 일자별 일정 조회 | 멤버 |
| `DELETE` | `/api/rooms/{roomId}/schedules/{scheduleId}` | 일정 삭제 (하위 items 포함) | 멤버 |

#### 6-2. 일정 항목 (장소 단위)

| 메서드 | 경로 | 설명 | 권한 |
|--------|------|------|------|
| `POST` | `/api/rooms/{roomId}/schedules/{scheduleId}/items` | 일정에 장소 추가 | 멤버 |
| `GET` | `/api/rooms/{roomId}/schedules/{scheduleId}/items` | 특정 일자 장소 목록 (order_index 순) | 멤버 |
| `DELETE` | `/api/rooms/{roomId}/schedules/{scheduleId}/items/{itemId}` | 일정 항목 삭제 | 멤버 |
| `PATCH` | `/api/rooms/{roomId}/schedules/{scheduleId}/items/order` | 순서 변경 (D&D) → WS 브로드캐스트 트리거 | 멤버 |
| `PATCH` | `/api/rooms/{roomId}/schedules/{scheduleId}/items/{itemId}/memo` | 메모 수정 | 멤버 |
| `PATCH` | `/api/rooms/{roomId}/schedules/{scheduleId}/items/{itemId}/time` | 시간 설정 (start_time, duration_minutes) | 멤버 |
| `GET` | `/api/rooms/{roomId}/schedules/{scheduleId}/items/{itemId}/travel` | 이동 정보 조회 | 멤버 |
| `PATCH` | `/api/rooms/{roomId}/schedules/{scheduleId}/items/{itemId}/travel-mode` | 이동 수단 변경 → Routes API 비동기 재호출 | 멤버 |

> **이동 정보 갱신 흐름:**
> `PATCH /items/order` (HTTP) → WS 브로드캐스트 → `@Async` Routes API 호출 → DB 저장 → WS 브로드캐스트

---

### 7. 채팅 메시지 (Messages)

| 메서드 | 경로 | 설명 | 권한 |
|--------|------|------|------|
| `GET` | `/api/rooms/{roomId}/messages` | 메시지 목록 조회 + 미수신 동기화 | 멤버 |

**쿼리 파라미터:**

| 파라미터 | 설명 | 예시 |
|----------|------|------|
| `before` | 해당 messageId 이전 메시지 (페이지네이션) | `?before=123&limit=50` |
| `after` | 해당 messageId 이후 메시지 (재접속 동기화) | `?after=99` |

> 메시지 **전송**은 HTTP가 아닌 STOMP로 처리합니다. 아래 STOMP 섹션 참조.

---

## STOMP 브로드캐스트 예상안 (서버 → 클라이언트)

> **현재 예상안 단계입니다.** Notion 명세에는 클라이언트 → 서버 방향만 기술되어 있으므로, 아래 항목들은 구현 시 별도로 확정해야 합니다.

### STOMP 엔드포인트 구조 예상안 (예시)

```
WS 연결:  /ws
구독:     /topic/rooms/{roomId}        — 방 전체 브로드캐스트
          /topic/rooms/{roomId}/schedule — 일정 변경 브로드캐스트
          /queue/user/{userId}          — 개인 알림 (미결)
발행:     /app/rooms/{roomId}/chat     — 클라이언트 → 서버 메시지 전송
```

### 서버 → 클라이언트 브로드캐스트 예상 목록

| 이벤트 | 구독 토픽 | 트리거 조건 | payload 타입 |
|--------|-----------|-------------|-------------|
| 채팅 메시지 수신 | `/topic/rooms/{roomId}` | 메시지 전송 시 | `MessageResponse` |
| 시스템 메시지 | `/topic/rooms/{roomId}` | 멤버 입장/퇴장/강퇴 | `MessageResponse` (SYSTEM) |
| 장소 카드 수신 | `/topic/rooms/{roomId}` | PLACE_SHARE 메시지 전송 시 | `MessageResponse` (PLACE_SHARE) |
| 일정 순서 변경 | `/topic/rooms/{roomId}/schedule` | `PATCH /items/order` 호출 시 | `ScheduleItemOrderResponse` |
| 이동 정보 갱신 완료 | `/topic/rooms/{roomId}/schedule` | Routes API 비동기 계산 완료 시 | `TravelInfoResponse` |
| AI 응답 수신 | `/topic/rooms/{roomId}` | AI 응답 생성 완료 시 | `MessageResponse` (AI_RESPONSE) |
| 멤버 접속 상태 변경 | `/topic/rooms/{roomId}` | WebSocket 연결/해제 시 | `MemberStatusResponse` |

> 위 목록은 예상 이벤트이며 확정 스펙이 아닙니다. 구현 전 팀과 확정이 필요합니다.

---

## 클라이언트 → 서버 STOMP 발행

| 목적지 (destination) | 설명 | payload 타입 |
|----------------------|------|-------------|
| `/app/rooms/{roomId}/chat` | 텍스트 채팅 전송 | `ChatMessageRequest` |
| `/app/rooms/{roomId}/chat` | 장소 카드 공유 (`message_type: PLACE_SHARE`) | `PlaceShareRequest` |

---

## 미결 사항

| # | 항목 | 현황 |
|---|------|------|
| 1 | AI 응답 저장 방식 | `messages.message_type` vs `ai_responses` 별도 테이블 |
| 2 | STOMP 구독 토픽 구조 확정 | 일정/채팅/멤버 이벤트 토픽 분리 여부 |
| 3 | 개인 알림 채널 (`/queue/user/{userId}`) 필요 여부 | 강퇴 알림 등 개인 대상 메시지 처리 방식 |
| 4 | 멤버 접속 상태 조회 방식 | 멤버 목록 API에 포함 vs 별도 엔드포인트 |
| 5 | 일정 변경 브로드캐스트 범위 | 순서 변경만 vs 추가/삭제도 포함 |
