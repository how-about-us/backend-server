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
| created_by | BIGINT | 사용자 ID 참조, NOT NULL | 방 생성자 (현재 구현은 users.id 값을 보관하지만 DB FK 제약은 두지 않음) |
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
| role | VARCHAR(20) | NOT NULL | HOST / MEMBER / PENDING (DEFAULT 없이 명시적 지정) |
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

## 5. bookmark_categories (북마크 카테고리)

방별 사용자 정의 북마크 카테고리. 북마크는 반드시 하나의 카테고리에 속합니다.

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| room_id | UUID | FK → rooms.id, NOT NULL | |
| name | VARCHAR(50) | NOT NULL | 방 내 카테고리 이름 |
| color_code | VARCHAR(7) | NOT NULL | 카테고리 색상 코드 (`#RRGGBB`) |
| created_by | BIGINT | 사용자 ID 참조, NULL 가능 | 생성한 사용자 (현재는 인증 연동 전이라 임시로 nullable) |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 수정일시 |

**제약:** UNIQUE(room_id, name)

**인덱스:** (room_id)

> 카테고리는 방 생성 직후 0개일 수 있습니다. 북마크 생성 시에는 반드시 현재 방 소속 카테고리를 지정해야 하며, 카테고리 생성/수정 시 색상 코드(`#RRGGBB`)를 함께 저장합니다. 카테고리 삭제 시 소속 북마크를 먼저 삭제한 뒤 카테고리를 삭제합니다.

---

## 6. bookmarks (장소 보관함 / 후보지)

방 내 공유 후보지 목록. 팀원이 후보로 등록한 장소.

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| room_id | UUID | FK → rooms.id, NOT NULL | |
| category_id | BIGINT | FK → bookmark_categories.id, NOT NULL | 현재 방 소속 카테고리 |
| google_place_id | VARCHAR(300) | NOT NULL | Google Place ID |
| added_by | BIGINT | 사용자 ID 참조, NULL 가능 | 등록한 사용자 (현재는 인증 연동 전이라 임시로 nullable) |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 수정일시 |

**제약:** UNIQUE(room_id, google_place_id)

**인덱스:** (room_id), (category_id), (google_place_id)

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
| google_place_id | VARCHAR(300) | NOT NULL | Google Place ID |
| start_time | TIME | NULLABLE | 시작 시각 (예: 09:00) |
| duration_minutes | INT | NULLABLE | 체류 시간(분) |
| order_index | INT | NOT NULL | 정렬 순서 (목록 조회 시 0부터 연속 유지) |
| memo | TEXT | NULLABLE | 방문 메모 (1차 구현 범위 제외, 후속 단계 예정) |
| travel_mode | VARCHAR(20) | NULLABLE | 다음 장소까지 이동 수단 (DRIVING / WALKING / TRANSIT / BICYCLING) |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 수정일시 |

**인덱스:** (schedule_id, order_index), (google_place_id)

> **현재 구현 범위:** 장소 추가/조회/삭제, 시간 설정, D&D 순서 변경, 이동 수단(`travel_mode`) 변경, 이동 정보 조회를 제공합니다. 항목 삭제·순서 변경 시 남은 `order_index`는 0부터 연속되도록 재정렬합니다.
>
> **이동 정보 흐름:** `distance_meters`, `duration_seconds`는 Google Maps Platform 정책상 DB에 영구 저장할 수 없습니다. 서버가 Google Routes API(Compute Routes)를 프록시하여 결과를 직접 클라이언트에 반환하며, Redis에 3분 TTL로 임시 캐시합니다(`route::{origin}:{dest}:{mode}`). `travel_mode`는 사용자 입력값이므로 DB에 저장합니다. 마지막 장소는 다음 장소가 없으므로 이동 정보를 반환하지 않습니다(204).

---

## 테이블 관계 요약

| 관계 | 유형 | 설명 |
|------|------|------|
| users ↔ rooms | 1:N | 한 유저가 여러 방 생성 가능 |
| users ↔ room_members | 1:N | 한 유저가 여러 방에 참여 |
| users ↔ messages | 1:N | 한 유저가 여러 메시지 입력 가능 |
| rooms ↔ room_members | 1:N | 한 방에 여러 멤버 |
| rooms ↔ messages | 1:N | 한 방에 여러 메시지 |
| rooms ↔ bookmark_categories | 1:N | 한 방에 여러 북마크 카테고리 |
| rooms ↔ bookmarks | 1:N | 한 방에 여러 후보지 |
| bookmark_categories ↔ bookmarks | 1:N | 한 카테고리에 여러 후보지 |
| rooms ↔ schedules | 1:N | 한 방에 여러 일자 |
| schedules ↔ schedule_items | 1:N | 한 일자에 여러 방문 장소 |

---

## Redis 관리 데이터 (ERD 외)

| 키 패턴 | 용도 | TTL | 설명 |
|---------|------|-----|------|
| `room:{roomId}:metadata` | 방 메타데이터 캐시 (DB 스냅샷) | Sliding TTL | |
| `room:{roomId}:connected_users` | 현재 접속 중인 유저 목록 (ephemeral) | 세션 종료 시 제거 | |
| `room:{roomId}:sessions:{userId}` | 접속 유저별 WebSocket/STOMP 세션 목록 (ephemeral) | 세션 종료 시 제거 | 같은 유저의 다중 탭/다중 세션 접속 상태 정합성 유지 |
| `place:detail:{googlePlaceId}` | 장소 상세 조회 결과 캐시 | 5분 | |
| `route::{origin}:{dest}:{travelMode}` | Routes API 이동 정보 캐시 | 3분 | Google Maps Platform 정책상 영구 저장 불가, 임시 캐시만 허용 |
| `refresh:token:{uuid}` | userId (String) | 14일 | 토큰 → 유저 매핑, 유효성 검증 |
| `refresh:user:{userId}` | Set\<uuid\> | 없음 | 유저 활성 토큰 목록, 전체 무효화 & Replay Detection |

---

## 설계 포인트

1. **초대 코드 고정 방식:** 방 생성 시 invite_code가 1개 자동 발급. 링크 유출 시 방장이 코드를 재발급. 별도 room_invitations 테이블 없이 단순하게 유지.
2. **google_place_id 직접 참조:** places 중간 테이블 없이 bookmarks·schedule_items에서 google_place_id(VARCHAR)를 직접 저장한다. 단순 검색 결과를 DB에 eager insert할 필요가 없고, 북마크/일정 추가 시점에만 장소 식별자가 기록된다. 각 테이블의 google_place_id 컬럼에 인덱스를 부여해 조회 성능을 확보한다.
3. **message.id auto-increment:** WebSocket 재접속 시 마지막 수신 ID 기반 미수신 메시지 조회 패턴에 최적화.
4. **room.id UUID:** 초대 URL에 노출되므로 추측 불가능한 UUID 사용.
5. **장소 상세 캐시:** 자유도가 높은 검색어는 캐시 히트율이 낮을 수 있으므로 검색 결과는 캐시하지 않는다. 대신 Google Place 상세 조회 응답은 `google_place_id` 기준으로 Redis에 5분 TTL로 저장한다.
6. **schedule_items.order_index:** D&D UI를 위한 정렬 인덱스. 재정렬 시 해당 컬럼만 업데이트.
7. **이동 정보 프록시:** `travel_mode`(이동 수단 선호)만 DB에 저장. Google Maps Platform 정책상 `distance_meters`·`duration_seconds`는 DB에 영구 저장 불가 — 서버가 Routes API를 프록시하여 결과를 클라이언트에 직접 반환하고, Redis 3분 TTL로 임시 캐시.
8. **방 Hard Delete:** 모든 하위 엔티티 FK에 `@OnDelete(CASCADE)` (DB `ON DELETE CASCADE`)를 적용하여, `roomRepository.delete(room)` 한 줄로 Room과 하위 데이터를 삭제한다. 단방향 관계를 유지하면서 DB가 cascade 삭제를 처리한다.

---

## TODO / 논의 필요 사항

- [ ] AI 응답 이력을 별도 테이블로 분리할지, messages.message_type으로 관리할지
- [ ] 예산 정리 기능을 위한 expenses 테이블 추가 여부
- [ ] 숙소/항공권 예약 연동 시 external_bookings 테이블 필요 여부
- [ ] bookmark 투표 기능 추가 시 bookmark_votes 테이블 필요
- [x] 방 삭제 정책: hard delete 전환 완료 (DB ON DELETE CASCADE)
- [ ] message 테이블 파티셔닝 전략 (방별, 날짜별 등)
- [ ] 초대 링크 만료/사용 횟수 제한 필요 시 room_invitations 테이블 분리 검토
