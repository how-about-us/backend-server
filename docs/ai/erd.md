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

## 5. bookmarks (장소 보관함 / 후보지)

방 내 공유 후보지 목록. 팀원이 후보로 등록한 장소.

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| room_id | UUID | FK → rooms.id, NOT NULL | |
| google_place_id | VARCHAR(300) | NOT NULL | Google Place ID |
| added_by | BIGINT | FK → users.id, NOT NULL | 등록한 사용자 |
| memo | TEXT | NULLABLE | 메모 |
| category | VARCHAR(30) | NOT NULL, DEFAULT 'ALL' | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 수정일시 |

**제약:** UNIQUE(room_id, google_place_id)

**인덱스:** (room_id), (google_place_id)

---

## 6. schedules (일자별 일정)

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

## 7. schedule_items (일정 항목)

일자별 방문 장소 및 시간 배치. Drag and Drop 정렬 지원.

| 컬럼 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| schedule_id | BIGINT | FK → schedules.id, NOT NULL | |
| google_place_id | VARCHAR(300) | NOT NULL | Google Place ID |
| start_time | TIME | NULLABLE | 시작 시각 (예: 09:00) |
| duration_minutes | INT | NULLABLE | 체류 시간(분) |
| order_index | INT | NOT NULL | 정렬 순서 (D&D용) |
| memo | TEXT | NULLABLE | 메모 |
| travel_mode | VARCHAR(20) | NULLABLE | 다음 장소까지 이동 수단 (DRIVING / WALKING / TRANSIT / BICYCLING) |
| distance_meters | INT | NULLABLE | 다음 장소까지 이동 거리 (미터) |
| duration_seconds | INT | NULLABLE | 다음 장소까지 예상 이동 시간 (초) |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 수정일시 |

**인덱스:** (schedule_id, order_index), (google_place_id)

> **이동 정보 갱신 흐름:** 장소 추가/삭제/순서 변경 시 HTTP 요청으로 order_index를 DB에 반영한 뒤, 변경 사항을 WebSocket으로 브로드캐스트합니다. 이후 @Async로 Routes API(Compute Routes)를 호출하여 영향받는 구간의 distance_meters, duration_seconds를 갱신하고, 결과를 다시 WebSocket으로 브로드캐스트합니다. 마지막 장소는 다음 장소가 없으므로 이동 정보가 NULL입니다.

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
| schedules ↔ schedule_items | 1:N | 한 일자에 여러 방문 장소 |

---

## Redis 관리 데이터 (ERD 외)

| 키 패턴 | 용도 | TTL |
|---------|------|-----|
| `room:{roomId}:metadata` | 방 메타데이터 캐시 (DB 스냅샷) | Sliding TTL |
| `room:{roomId}:connected_users` | 현재 접속 중인 유저 목록 (ephemeral) | 세션 종료 시 제거 |
| `place:detail:{googlePlaceId}` | 장소 상세 조회 결과 캐시 | 10분 |

---

## 설계 포인트

1. **초대 코드 고정 방식:** 방 생성 시 invite_code가 1개 자동 발급. 링크 유출 시 방장이 코드를 재발급. 별도 room_invitations 테이블 없이 단순하게 유지.
2. **google_place_id 직접 참조:** places 중간 테이블 없이 bookmarks·schedule_items에서 google_place_id(VARCHAR)를 직접 저장한다. 단순 검색 결과를 DB에 eager insert할 필요가 없고, 북마크/일정 추가 시점에만 장소 식별자가 기록된다. 각 테이블의 google_place_id 컬럼에 인덱스를 부여해 조회 성능을 확보한다.
3. **message.id auto-increment:** WebSocket 재접속 시 마지막 수신 ID 기반 미수신 메시지 조회 패턴에 최적화.
4. **room.id UUID:** 초대 URL에 노출되므로 추측 불가능한 UUID 사용.
5. **장소 상세 캐시:** 자유도가 높은 검색어는 캐시 히트율이 낮을 수 있으므로 검색 결과는 캐시하지 않는다. 대신 Google Place 상세 조회 응답은 `google_place_id` 기준으로 Redis에 10분 TTL로 저장한다.
6. **schedule_items.order_index:** D&D UI를 위한 정렬 인덱스. 재정렬 시 해당 컬럼만 업데이트.
7. **이동 정보 비동기 갱신:** travel_mode, distance_meters, duration_seconds는 "현재 장소 → 다음 장소" 구간 이동 정보 저장. 마지막 장소의 이동 정보는 NULL.
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
