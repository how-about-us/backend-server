# 기능 명세서

> ERD 초안 기준으로 정리한 기능 목록입니다. 각 기능은 API 명세서 작성의 단위가 됩니다.

## 프로젝트 개요

다같이 여행 계획을 세우는 협업 웹 서비스.

- **화면 구성**: 좌측 지도 / 우측 채팅·일정·보관함
- **핵심 기능**: AI 에이전트가 채팅방 팀원으로 참여해 여행 계획 수립 보조

---

## 구현 상태 범례

| 상태 | 의미 |
|------|------|
| `[ ]` | 미구현 |
| `[x]` | 구현 완료 |
| `[-]` | 보류 / MVP 이후 |

---

## 1. 인증 (Auth)

| 상태 | 기능 | 설명 | ERD 연관 |
|------|------|------|----------|
| `[x]` | 구글 OAuth 로그인 | Google 계정으로 소셜 로그인 | users |
| `[x]` | 토큰 재발급 (Refresh) | Refresh Token Rotation: UUID 기반 HTTP-only 쿠키(path=/auth/refresh), Redis `refresh:token:{uuid}`→userId(TTL 14일) / `refresh:user:{userId}`→Set\<uuid\>. Replay Detection 으로 탈취 시 전체 무효화 | Redis |
| `[x]` | 로그아웃 | 단일 기기 로그아웃: 요청한 토큰만 삭제 | Redis |
| `[x]` | 내 정보 조회 | 로그인된 사용자 프로필 조회 | users |

---

## 2. 여행 방 (Rooms)

| 상태 | 기능 | 설명 | ERD 연관 |
|------|------|------|----------|
| `[x]` | 방 생성 | 방 제목, 여행지, 날짜 입력 → invite_code 자동 발급 | rooms |
| `[x]` | 내 방 목록 조회 | 내가 참여 중인 방 목록 | rooms, room_members |
| `[x]` | 방 상세 조회 | 방 메타정보 (제목, 여행지, 날짜, 멤버 수 등) | rooms |
| `[x]` | 방 수정 | 방 제목, 여행지, 날짜 수정 (HOST만) | rooms |
| `[x]` | 방 삭제 | 방 삭제 (HOST만, hard delete) | rooms, room_members |
| `[x]` | 초대 코드 재발급 | 기존 invite_code 폐기 후 신규 발급 (HOST만) | rooms |
| `[x]` | 초대 코드로 입장 요청 | invite_code로 PENDING 멤버 등록. 이미 멤버면 멱등 처리(200), 이미 PENDING이면 대기 상태 반환(202) | rooms, room_members |
| `[x]` | 입장 상태 조회 | 입장 요청자가 자신의 승인 상태 확인 (pending / approved / 404=거절) | room_members |
| `[x]` | 대기 입장 요청 목록 조회 | HOST가 PENDING 상태 멤버 목록 조회 | room_members |
| `[x]` | 입장 승인 | HOST가 PENDING → MEMBER로 변경 | room_members |
| `[x]` | 입장 거절 | HOST가 PENDING 멤버 레코드 삭제 | room_members 

---

## 3. 멤버 관리 (Room Members)

| 상태 | 기능 | 설명 | ERD 연관 |
|------|------|------|----------|
| `[x]` | 방 멤버 목록 조회 | 방 참여자 목록 + 역할(HOST/MEMBER) + 접속 상태 | room_members |
| `[ ]` | 멤버 추방 | HOST가 특정 멤버 추방 (HOST는 추방 불가) | room_members |
| `[ ]` | 방 나가기 | 본인이 방에서 탈퇴 | room_members |
| `[x]` | 실시간 방 접속 상태 추적 | 유효한 access_token 쿠키가 있는 사용자만 WebSocket handshake를 허용한다. SockJS + STOMP 방 topic 구독 성공 시 Redis에 접속 유저를 기록하고 접속 이벤트를 브로드캐스트한다. 새로 온라인이 된 유저의 접속 이벤트에는 `userId`, `nickname`, `profileImageUrl`을 포함해 클라이언트가 방 멤버 프로필 맵을 갱신할 수 있게 한다. 세션 종료 시 제거와 해제 이벤트를 브로드캐스트한다 | Redis (connected_users) |
| `[x]` | 현재 접속 중인 유저 조회 | 멤버 목록 API(`GET /rooms/{roomId}/members`)의 `isOnline` 필드로 접속 상태 포함 | Redis (connected_users) |
| `[-]` | 방장 위임 | 권한 이전 | room_members |

> **실시간 협업 이벤트:** 방 진입 시 클라이언트는 HTTP 조회로 초기 상태를 가져오고, 이후 STOMP 이벤트로 변경분을 반영한다. 접속 상태는 `/topic/rooms/{roomId}/presence`, 보관함 변경은 `/topic/rooms/{roomId}/bookmarks`, 일정 변경은 `/topic/rooms/{roomId}/schedules`로 브로드캐스트한다.

---

## 4. 장소 (Places)

> `places` 테이블 없이 `google_place_id`를 직접 사용한다. 검색은 캐시하지 않고, 장소 상세 조회 payload는 Redis에 5분 TTL로 캐시한다.

| 상태 | 기능 | 설명 | ERD 연관 |
|------|------|------|----------|
| `[x]` | 장소 검색 | Google Places API (New)로 장소 검색 | - |
| `[x]` | 장소 상세 조회 | 장소명, 주소, 평점, 전화번호, 웹사이트, 영업시간, 사진 목록(`photoNames`) 등, 상세 조회 결과는 Redis에 5분 TTL 캐시 | Redis |
| `[x]` | 장소 사진 URL 조회 | `photoName`을 받아 Google Photo Media API를 호출, `photoUrl` 반환. 캐시 없음 | - |

---

## 5. 보관함 (Bookmarks)

> 접근 권한: 방의 HOST 또는 MEMBER만 조회·생성·수정·삭제할 수 있다. PENDING 또는 비멤버는 접근할 수 없다.

| 상태 | 기능 | 설명 | ERD 연관 |
|------|------|------|----------|
| `[x]` | 보관함에 장소 추가 | 검색된 장소를 방의 후보지로 등록. 생성 시 방 소속 `categoryId`가 필수 | bookmarks, bookmark_categories |
| `[x]` | 보관함 목록 조회 | 방의 후보지 목록 조회, `categoryId` 쿼리 파라미터로 카테고리별 필터링 지원 | bookmarks |
| `[x]` | 보관함 항목 삭제 | 후보지에서 제거 | bookmarks |
| `[x]` | 보관함 카테고리 목록 조회 | 방에서 사용 가능한 북마크 카테고리 목록 조회, 색상 코드 포함 | bookmark_categories |
| `[x]` | 보관함 카테고리 생성 | 방별 사용자 정의 카테고리 생성, 색상 코드 필수 | bookmark_categories |
| `[x]` | 보관함 카테고리 수정 | 기존 카테고리 이름과 색상 코드 수정 | bookmark_categories |
| `[x]` | 보관함 카테고리 삭제 | 카테고리 삭제 시 소속 북마크도 함께 삭제 | bookmark_categories, bookmarks |
| `[x]` | 보관함 카테고리 변경 | 항목 카테고리 수정 | bookmarks, bookmark_categories |
| `[x]` | 장소 채팅에 공유 | 보관함/검색 장소를 `PLACE_SHARE` 채팅 메시지로 전송. 서버는 장소 카드 표시용 스냅샷(`googlePlaceId`, `name`, `formattedAddress`, `latitude`, `longitude`, `rating`, `photoName`)을 metadata에 저장한다 | bookmarks → MongoDB messages |

---

## 6. 일정 (Schedules)

> 접근 권한: 방의 HOST 또는 MEMBER만 조회·생성·수정·삭제할 수 있다. PENDING 또는 비멤버는 접근할 수 없다.

### 6-1. 일정 (Schedules — 일자 단위)

| 상태 | 기능 | 설명 | ERD 연관 |
|------|------|------|----------|
| `[x]` | 일정 생성 | N일차 + 날짜 등록 | schedules |
| `[x]` | 일정 목록 조회 | 방의 전체 일자별 일정 조회 | schedules |
| `[x]` | 일정 삭제 | 특정 일자 삭제 (하위 items 포함) | schedules |

### 6-2. 일정 항목 (Schedule Items — 장소 단위)

> **이동 정보 흐름:** 순서 변경(HTTP) → DB 반영 → 클라이언트가 영향받는 구간을 병렬로 `/route` 조회 → 서버가 Google Routes API 프록시 (Redis 3분 캐시). Google Maps Platform 정책상 결과(distance, duration)를 DB에 영구 저장하지 않음.

| 상태 | 기능 | 설명 | ERD 연관 |
|------|------|------|----------|
| `[x]` | 일정에 장소 추가 | 특정 일자에 장소 추가 (보관함 또는 검색에서 바로) | schedule_items |
| `[x]` | 일정 항목 목록 조회 | 특정 일자의 장소 목록 (order_index 순) | schedule_items |
| `[x]` | 일정 항목 삭제 | 일자에서 장소 제거 | schedule_items |
| `[x]` | 일정 순서 변경 (D&D) | order_index 재정렬, 변경된 전체 목록 반환 | schedule_items |
| `[-]` | 일정 항목 메모 수정 | 1차 구현 범위 제외 | schedule_items |
| `[x]` | 시간 설정 | start_time, duration_minutes 설정 | schedule_items |
| `[x]` | 이동 정보 조회 | 현재→다음 장소 구간의 distance_meters, duration_seconds, travel_mode 반환. 마지막 항목은 204. 결과는 Redis 3분 캐시, DB 저장 없음 | schedule_items, Redis |
| `[x]` | 이동 수단 변경 | travel_mode 수동 변경 (DB 저장) → 클라이언트가 /route 재조회 | schedule_items |

---

## 7. 채팅 (Messages)

| 상태 | 기능 | 설명 | ERD 연관 |
|------|------|------|----------|
| `[x]` | 일반 채팅 메시지 전송 (WS) | 클라이언트가 `/app/rooms/{roomId}/messages/chat`로 `clientMessageId`, `content`를 전송하면 MongoDB `messages` 컬렉션에 `messageType=CHAT`, `metadata={}`로 저장 후 `/topic/rooms/{roomId}/messages`로 브로드캐스트. 실패 시 발신자에게만 `/user/queue/errors`로 전달 | MongoDB messages |
| `[x]` | 메시지 목록 조회 | 방 채팅 히스토리 조회. `afterId`가 없으면 최근 메시지, 있으면 해당 Mongo `_id` 이후 메시지 조회 | MongoDB messages |
| `[x]` | 재접속 시 미수신 메시지 동기화 | 마지막 수신 Mongo message `_id` 이후 메시지 조회 | MongoDB messages |
| `[x]` | 장소 카드 메시지 전송 | 클라이언트가 `/app/rooms/{roomId}/messages/place`로 장소 스냅샷을 전송하면 MongoDB `messages` 컬렉션에 `messageType=PLACE_SHARE`로 저장 후 `/topic/rooms/{roomId}/messages`로 브로드캐스트 | MongoDB messages |
| `[x]` | 시스템 메시지 | 입장 승인 같은 멤버십 변경 이벤트를 `messageType=SYSTEM`, `senderId=NULL` 메시지로 저장 후 브로드캐스트. WebSocket 접속/해제 presence 이벤트는 채팅 히스토리에 저장하지 않음 | MongoDB messages |

---

## 8. AI 기능

> **미결:** AI 응답 이력을 MongoDB `messages` 컬렉션의 `messageType=AI_RESPONSE`으로 관리할지 별도 컬렉션으로 분리할지, 어떤 컨텍스트(방 일정, 보관함, 위치 등)를 전달할지 결정 필요.

| 상태 | 기능 | 설명 | ERD 연관 |
|------|------|------|----------|
| `[ ]` | AI 호출 (룰 기반 트리거) | 채팅창에서 AI 호출 버튼으로 질의 | MongoDB messages (messageType: AI_RESPONSE) |
| `[ ]` | AI 응답 채팅에 표시 | AI 응답을 senderId=NULL 메시지로 채팅에 노출 | MongoDB messages |

---

## 미결 사항

| # | 항목 | 선택지 | 현황 |
|---|------|--------|------|
| 1 | AI 응답 저장 방식 | MongoDB messages.messageType vs ai_responses 별도 컬렉션 | 미결 |
| 2 | schedules 기준값 | day_number, date 두 컬럼 모두 저장 | 결정 |
| 3 | schedule_items order_index 중복 방지 | UNIQUE 제약 vs gap 전략 | 미결 |
| 4 | room_members 직접 참조 정합성 | sender_id, added_by → users 직접 vs room_members 참조 | 미결 |
| 5 | 방장 위임 기능 | MVP 이후 진행 | 보류 |
| 6 | 방 삭제 정책 | hard delete 전환 완료 (DB ON DELETE CASCADE) | 확정 |
| 7 | 초대 링크 만료/횟수 제한 | room_invitations 테이블 분리 시점 기준 | 미결 |
