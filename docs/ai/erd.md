# ERD 명세서

> **초안 문서입니다.** 구현 과정에서 컬럼 및 관계가 변경될 수 있습니다.

- **기술 스택:** PostgreSQL + PostGIS, Redis (세션/토큰)
- **ID 전략:** roomId → UUID (초대 URL 보안), messageId → auto-increment (재접속 동기화용), 나머지 → BIGINT auto-increment

---

## 1. users (사용자)

Google OAuth 기반 사용자 정보

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 사용자 고유 ID |
| email | VARCHAR(255) | UNIQUE, NOT NULL | 구글 이메일 |
| nickname | VARCHAR(50) | NOT NULL | 표시 이름 |
| profile_image_url | VARCHAR(500) | NULLABLE | 프로필 이미지 URL |
| provider | VARCHAR(20) | NOT NULL, DEFAULT 'GOOGLE' | OAuth 제공자 |
| provider_id | VARCHAR(255) | NOT NULL | OAuth 제공자 측 사용자 ID |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 가입일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 수정일시 |

**제약:** UNIQUE(provider, provider_id)

---

## 2. rooms (여행 방)

여행 계획 협업 방. 방 생성 시 invite_code가 자동 발급되며, `/invite/{invite_code}` 경로로 입장합니다.

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | UUID | PK, DEFAULT gen_random_uuid() | 방 고유 ID |
| title | VARCHAR(100) | NOT NULL | 방 제목 |
| destination | VARCHAR(200) | NULLABLE | 여행지 (예: 도쿄, 제주도) |
| start_date | DATE | NULLABLE | 여행 시작일 |
| end_date | DATE | NULLABLE | 여행 종료일 |
| invite_code | VARCHAR(50) | UNIQUE, NOT NULL | 초대 링크용 고정 코드 (방 생성 시 자동 발급) |
| created_by | BIGINT | FK → users.id, NOT NULL | 방 생성자 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 수정일시 |

> 방 1개당 초대 코드 1개가 고정됩니다. 링크 유출 시 방장이 invite_code를 재발급(갱신)하는 방식으로 대응합니다. 만료 시간, 사용 횟수 제한 등 세밀한 초대 관리가 필요해지면 별도 room_invitations 테이블로 분리를 검토합니다.

---

## 3. room_members (방 참여자)

사용자-방 매핑 및 권한 관리

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| room_id | UUID | FK → rooms.id, NOT NULL | |
| user_id | BIGINT | FK → users.id, NOT NULL | |
| role | VARCHAR(20) | NOT NULL, DEFAULT 'MEMBER' | ADMIN / MEMBER |
| joined_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 참여 일시 |
| last_read_message_id | BIGINT | NULLABLE | 마지막으로 읽은 메시지 ID |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 수정일시 |

**제약:** UNIQUE(room_id, user_id)

**인덱스:** (room_id), (user_id)

---

## 4. messages (채팅 메시지)

실시간 채팅 메시지. auto-increment ID로 재접속 시 동기화 기준점 활용.

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 순차 비교용 |
| room_id | UUID | FK → rooms.id, NOT NULL | |
| sender_id | BIGINT | FK → users.id, NULLABLE | NULL = 시스템/AI 메시지 |
| content | TEXT | NOT NULL | 메시지 내용 |
| message_type | VARCHAR(30) | NOT NULL, DEFAULT 'CHAT' | CHAT / AI_RESPONSE / PLACE_SHARE / SYSTEM |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 수정일시 |

**인덱스:** (room_id, id) — 방 내 메시지 순차 조회용

> 재접속 시 클라이언트가 마지막으로 수신한 message.id를 보내면, 서버는 해당 ID 이후의 메시지만 전달합니다.

**미결:** AI 응답 이력을 이 테이블의 message_type으로 관리할지, 별도 ai_responses 테이블로 분리할지 결정 필요.

---

## 5. places (장소)

Google Place ID만 DB에 유지. 나머지 장소 정보(name, address, 좌표, rating 등)는 Redis에 단기 캐싱.

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| google_place_id | VARCHAR(300) | UNIQUE, NOT NULL | Google Place ID (영구 저장 허용) |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 수정일시 |

> **[정책 결정]** Google Places API 이용약관상 `place_id`만 영구 저장 허용. `name`, `address`, 위경도, `rating`, `photo_reference`는 저장 금지(위경도는 30일 임시 캐싱만 허용). 장소 상세 정보는 `place:{googlePlaceId}` Redis 키로 TTL 30일 캐싱으로 대응. 공간 쿼리(PostGIS) 기능은 포기.
> 관련 정책: [Places API Policies](https://developers.google.com/maps/documentation/places/web-service/policies)

---

## 6. bookmarks (장소 보관함 / 후보지)

방 내 공유 후보지 목록. 팀원이 후보로 등록한 장소.

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| room_id | UUID | FK → rooms.id, NOT NULL | |
| place_id | BIGINT | FK → places.id, NOT NULL | |
| added_by | BIGINT | FK → users.id, NOT NULL | 등록한 사용자 |
| memo | TEXT | NULLABLE | 메모 |
| category | VARCHAR(30) | NOT NULL, DEFAULT 'ALL' | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 수정일시 |

**제약:** UNIQUE(room_id, place_id)

**인덱스:** (room_id)

---

## 7. schedules (일자별 일정)

여행 일정의 일자 단위 그룹

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| room_id | UUID | FK → rooms.id, NOT NULL | |
| day_number | INT | NOT NULL | 여행 N일차 (1부터 시작) |
| date | DATE | NOT NULL | 해당 날짜 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 수정일시 |

**제약:** UNIQUE(room_id, date), UNIQUE(room_id, day_number)

---

## 8. schedule_items (일정 항목)

일자별 방문 장소 및 시간 배치. Drag and Drop 정렬 지원.

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| schedule_id | BIGINT | FK → schedules.id, NOT NULL | |
| place_id | BIGINT | FK → places.id, NOT NULL | |
| start_time | TIME | NULLABLE | 시작 시각 (예: 09:00) |
| duration_minutes | INT | NULLABLE | 체류 시간(분) |
| order_index | INT | NOT NULL | 정렬 순서 (D&D용) |
| memo | TEXT | NULLABLE | 메모 |
| travel_mode | VARCHAR(20) | NULLABLE | 다음 장소까지 이동 수단 (DRIVING / WALKING / TRANSIT / BICYCLING) — 사용자 직접 설정값 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 수정일시 |

**인덱스:** (schedule_id, order_index)

> **이동 정보 갱신 흐름:** 장소 추가/삭제/순서 변경 시 HTTP 요청으로 order_index를 DB에 반영한 뒤, 변경 사항을 WebSocket으로 브로드캐스트합니다. 이후 @Async로 Routes API(Compute Routes)를 호출하여 영향받는 구간의 distance_meters, duration_seconds를 `route:{fromGooglePlaceId}:{toGooglePlaceId}:{travelMode}` Redis 키(TTL 30일)에 저장하고, 결과를 WebSocket으로 브로드캐스트합니다. 마지막 장소는 이동 정보 없음.

> **[정책 결정]** Google Routes API 이용약관상 `distance_meters`, `duration_seconds`는 DB 영구 저장 금지. Redis TTL 30일 캐싱으로 대응. `travel_mode`는 사용자가 직접 설정하는 값이므로 DB 저장 유지.
> 관련 정책: [Routes API Policies](https://developers.google.com/maps/documentation/routes/policies)

---

## 테이블 관계 요약

| 관계 | 유형 | 설명 |
|------|------|------|
| users ↔ rooms | 1:N | 한 유저가 여러 방 생성 가능 |
| users ↔ room_members | 1:N | 한 유저가 여러 방에 참여 |
| users ↔ messages | 1:N | 한 유저가 여러 메시지 입력 가능 |
| rooms ↔ room_members | 1:N | 한 방에 여러 멤버 |
| rooms ↔ messages | 1:N | 한 방에 여러 메시지 |
| rooms ↔ bookmarks | 1:N | 한 방에 여러 후보지 |
| rooms ↔ schedules | 1:N | 한 방에 여러 일자 |
| places ↔ bookmarks | 1:N | 한 장소가 여러 방에서 후보 등록 가능 |
| places ↔ schedule_items | 1:N | 한 장소가 여러 일정에 포함 가능 |
| schedules ↔ schedule_items | 1:N | 한 일자에 여러 방문 장소 |

---

## Redis 관리 데이터 (ERD 외)

| 키 패턴 | 용도 | TTL |
|---------|------|-----|
| `room:{roomId}:metadata` | 방 메타데이터 캐시 (DB 스냅샷) | Sliding TTL |
| `room:{roomId}:connected_users` | 현재 접속 중인 유저 목록 (ephemeral) | 세션 종료 시 제거 |
| `place:{googlePlaceId}` | 장소 상세 정보 (name, address, lat, lng, category, rating, photo_name) | 30일 |
| `route:{fromGooglePlaceId}:{toGooglePlaceId}:{travelMode}` | 구간 이동 정보 (distance_meters, duration_seconds) | 30일 |

---

## 설계 포인트

1. **초대 코드 고정 방식:** 방 생성 시 invite_code가 1개 자동 발급. 링크 유출 시 방장이 코드를 재발급. 별도 room_invitations 테이블 없이 단순하게 유지.
2. **places 테이블 최소화:** Google Place ID만 DB에 유지. 장소 상세 정보는 Redis `place:{googlePlaceId}` 키로 TTL 30일 캐싱. Google API 정책 준수.
3. **message.id auto-increment:** WebSocket 재접속 시 마지막 수신 ID 기반 미수신 메시지 조회 패턴에 최적화.
4. **room.id UUID:** 초대 URL에 노출되므로 추측 불가능한 UUID 사용.
5. **이동 정보 Redis 캐싱:** distance_meters, duration_seconds는 `route:{from}:{to}:{travelMode}` 키로 TTL 30일 캐싱. travel_mode는 사용자 설정값이므로 DB 유지. Google API 정책 준수.
6. **schedule_items.order_index:** D&D UI를 위한 정렬 인덱스. 재정렬 시 해당 컬럼만 업데이트.
8. **Soft Delete 미적용 (초안):** 방 삭제 시 CASCADE 또는 별도 정책은 추후 논의.

---

## TODO / 논의 필요 사항

- [ ] AI 응답 이력을 별도 테이블로 분리할지, messages.message_type으로 관리할지
- [ ] 예산 정리 기능을 위한 expenses 테이블 추가 여부
- [ ] 숙소/항공권 예약 연동 시 external_bookings 테이블 필요 여부
- [ ] bookmark 투표 기능 추가 시 bookmark_votes 테이블 필요
- [ ] 방 삭제 정책: soft delete vs hard delete + CASCADE
- [ ] message 테이블 파티셔닝 전략 (방별, 날짜별 등)
- [ ] 초대 링크 만료/사용 횟수 제한 필요 시 room_invitations 테이블 분리 검토
- [ ] **[정책] places 테이블 재설계**: `name`, `address`, `rating`, `photo_reference` 저장은 Google Places API 이용약관 위반. `google_place_id`만 DB에 유지하고 나머지는 Redis 캐시 또는 매번 API 호출로 대체하는 방안 결정 필요.
- [ ] **[정책] schedule_items 이동 정보 저장 방식 재검토**: `distance_meters`, `duration_seconds`를 DB에 영구 저장하는 것은 Google Routes API 이용약관 위반. Redis 단기 캐시(TTL ≤ 30일) 또는 조회 시 재계산 방식으로 전환 필요.
