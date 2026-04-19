# 여행 방 (Rooms) 설계 문서

> 작성일: 2026-04-19
> 범위: Rooms 섹션 전체 — 방 CRUD + 초대 + 승인 입장

---

## 1. 개요

여행 계획 협업 서비스의 핵심 단위인 "여행 방"을 관리하는 기능이다. 방 생성, 조회, 수정, 삭제와 초대 코드 기반 입장(HOST 승인 필요)을 포함한다.

---

## 2. 결정 사항

| 항목 | 결정 | 이유 |
|------|------|------|
| invite_code 형식 | nanoid 스타일 10~12자 | 충돌 확률 낮고, URL 친화적이며, 추측 어려움 |
| 삭제 정책 | Soft delete (방만 `deletedAt`) | 복구 가능성 확보. 하위 데이터는 방 접근 차단으로 자연 필터링 |
| 초대 코드 재발급 | 단순 덮어쓰기 | MVP에 충분. 만료/횟수 제한은 이후 테이블 분리로 대응 가능 |
| 중복 입장 요청 | 멱등 처리 | 이미 멤버면 방 정보 반환, 이미 PENDING이면 대기 상태 반환 |
| 방 목록 정렬 | 참여 시간 역순 + 커서 기반 페이지네이션 | `room_members.joinedAt` 활용, 추가 컬럼 불필요 |
| 권한 체크 | 서비스 레이어 직접 체크 | HOST 권한 필요한 API가 6개뿐, 추상화는 과도 |
| 역할 명칭 | HOST / MEMBER / PENDING | ADMIN은 서비스 관리자와 혼동. HOST가 방 맥락에 적합 |
| Room PK | BIGINT `id` (내부) + UUID `uuid` (외부 노출) | UUID PK는 16바이트로 B-tree 인덱싱 비효율, FK도 비대해짐 |
| 승인 대기 방식 | HTTP 상태 조회 (새로고침 버튼) | SSE/Long Polling보다 구현이 단순하고, 클라이언트가 명시적으로 상태를 확인하므로 직관적 |

---

## 3. 패키지 구조

```
room/
├── entity/
│   ├── Room.java
│   ├── RoomMember.java
│   └── RoomRole.java              (enum: HOST, MEMBER, PENDING)
├── repository/
│   ├── RoomRepository.java
│   └── RoomMemberRepository.java
├── service/
│   ├── RoomService.java            (방 CRUD)
│   ├── RoomInviteService.java      (초대 코드 관리 + 입장 + 승인/거절)
│   └── dto/                        (서비스 레이어 DTO)
├── controller/
│   ├── RoomController.java
│   └── dto/                        (요청/응답 DTO)
└── util/
    └── InviteCodeGenerator.java
```

---

## 4. 엔티티 설계

### Room

```java
@Entity
@Table(name = "rooms")
public class Room extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                        // 내부 PK

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;                      // 외부 노출용

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 200)
    private String destination;

    private LocalDate startDate;
    private LocalDate endDate;

    @Column(unique = true, nullable = false, length = 50)
    private String inviteCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    private Instant deletedAt;              // soft delete
}
```

- 팩토리 메서드: `Room.create(title, destination, startDate, endDate, createdBy, inviteCode)`
- `delete()` → `deletedAt = Instant.now()`
- `regenerateInviteCode(newCode)` → 기존 코드 덮어쓰기
- `update(title, destination, startDate, endDate)` → null이 아닌 필드만 업데이트

### RoomMember

```java
@Entity
@Table(name = "room_members",
       uniqueConstraints = @UniqueConstraint(columns = {"room_id", "user_id"}))
public class RoomMember extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomRole role;

    @Column(nullable = false)
    private Instant joinedAt;
}
```

- 팩토리 메서드: `RoomMember.of(room, user, role)` — `joinedAt = Instant.now()`
- `approve()` → `role = MEMBER`

### RoomRole

```java
public enum RoomRole {
    HOST, MEMBER, PENDING
}
```

---

## 5. API 설계

### 5-1. 방 CRUD

| # | Method | URI | 설명 | 권한 |
|---|--------|-----|------|------|
| 1 | POST | `/rooms` | 방 생성 | 인증된 사용자 |
| 2 | GET | `/rooms` | 내 방 목록 조회 | 인증된 사용자 |
| 3 | GET | `/rooms/{roomUuid}` | 방 상세 조회 | 방 멤버 (MEMBER/HOST) |
| 4 | PATCH | `/rooms/{roomUuid}` | 방 수정 | HOST |
| 5 | DELETE | `/rooms/{roomUuid}` | 방 삭제 (soft) | HOST |

### 5-2. 초대 & 입장

| # | Method | URI | 설명 | 권한 |
|---|--------|-----|------|------|
| 6 | POST | `/rooms/{roomUuid}/invite-code` | 초대 코드 재발급 | HOST |
| 7 | POST | `/rooms/join` | 초대 코드로 입장 요청 | 인증된 사용자 |
| 8 | GET | `/rooms/join/status?inviteCode={code}` | 입장 상태 조회 | 입장 요청한 사용자 |
| 9 | GET | `/rooms/{roomUuid}/join-requests` | 대기 중인 입장 요청 목록 | HOST |
| 10 | POST | `/rooms/{roomUuid}/join-requests/{requestId}/approve` | 입장 승인 | HOST |
| 11 | POST | `/rooms/{roomUuid}/join-requests/{requestId}/reject` | 입장 거절 | HOST |

### 5-3. 요청/응답 상세

**방 생성 (POST /rooms)**
```json
// Request
{
  "title": "부산 여행",           // 필수, 1~100자
  "destination": "부산",          // 선택, ~200자
  "startDate": "2026-05-01",     // 선택
  "endDate": "2026-05-03"        // 선택, startDate 이후여야 함
}

// Response 201 Created
{
  "uuid": "550e8400-e29b-...",
  "title": "부산 여행",
  "destination": "부산",
  "startDate": "2026-05-01",
  "endDate": "2026-05-03",
  "inviteCode": "aB3xK9mQ2w",
  "role": "HOST",
  "createdAt": "2026-04-19T..."
}
```

**내 방 목록 (GET /rooms?cursor={joinedAt}&size=20)**
```json
// Response 200
{
  "rooms": [
    {
      "uuid": "...",
      "title": "부산 여행",
      "destination": "부산",
      "startDate": "2026-05-01",
      "endDate": "2026-05-03",
      "memberCount": 4,
      "role": "HOST",
      "joinedAt": "2026-04-19T..."
    }
  ],
  "nextCursor": "2026-04-18T...",
  "hasNext": true
}
```

**방 상세 (GET /rooms/{roomUuid})**
```json
// Response 200
{
  "uuid": "...",
  "title": "부산 여행",
  "destination": "부산",
  "startDate": "2026-05-01",
  "endDate": "2026-05-03",
  "inviteCode": "aB3xK9mQ2w",
  "memberCount": 4,
  "role": "MEMBER",
  "createdAt": "2026-04-19T..."
}
```

**방 수정 (PATCH /rooms/{roomUuid})**
```json
// Request — 변경할 필드만 전송
{
  "title": "부산 맛집 여행",
  "endDate": "2026-05-04"
}
```

**초대 코드로 입장 요청 (POST /rooms/join)**
```json
// Request
{
  "inviteCode": "aB3xK9mQ2w"
}

// Response 200 — 이미 MEMBER/HOST
{
  "status": "already_member",
  "uuid": "...",
  "title": "부산 여행",
  "role": "MEMBER"
}

// Response 202 Accepted — PENDING 등록
{
  "status": "pending",
  "roomTitle": "부산 여행"
}
```

**입장 상태 조회 (GET /rooms/join/status?inviteCode={code})**
```json
// Response 200 — 아직 대기 중
{
  "status": "pending",
  "roomTitle": "부산 여행"
}

// Response 200 — 승인됨
{
  "status": "approved",
  "uuid": "...",
  "roomTitle": "부산 여행",
  "role": "MEMBER"
}

// Response 404 — 거절됨 (레코드 삭제되어 조회 불가)
```

**입장 요청 목록 (GET /rooms/{roomUuid}/join-requests)**
```json
// Response 200
{
  "requests": [
    {
      "requestId": 42,
      "userId": "...",
      "nickname": "김철수",
      "profileImageUrl": "...",
      "requestedAt": "2026-04-19T..."
    }
  ]
}
```

### 5-4. 공통 사항

- 모든 API에서 외부 식별자는 `uuid` 사용, 내부 `id`는 노출하지 않음
- 방 멤버(MEMBER/HOST)가 아닌 사용자가 방 내부 API 접근 시 → 403
- PENDING 상태 사용자는 방 내부 API 접근 불가
- 삭제된 방 접근 시 → 404
- validation: title 필수(1~100자), destination 선택(~200자), startDate ≤ endDate

---

## 6. 서비스 로직 흐름

### RoomService (방 CRUD)

**방 생성**
1. 요청 DTO 검증 (title 필수, startDate ≤ endDate)
2. `InviteCodeGenerator`로 nanoid 생성
3. `Room.create(...)` 팩토리 메서드로 엔티티 생성
4. `Room` 저장
5. `RoomMember.of(room, user, HOST)` 생성 → 저장
6. 생성된 방 정보 반환

**내 방 목록 조회**
1. 현재 사용자의 `room_members` 조회 (joinedAt 역순, 커서 기반)
2. 조인으로 Room 정보 + memberCount 함께 가져옴
3. `deletedAt IS NULL` 필터 + `role != PENDING` 필터
4. `size + 1`개 조회해서 `hasNext` 판단, 마지막 항목의 `joinedAt`을 `nextCursor`로 반환

**방 상세 조회**
1. `roomUuid`로 Room 조회 (deletedAt IS NULL)
2. 현재 사용자가 방 멤버인지 확인 (MEMBER/HOST) → 아니면 403
3. 방 정보 + 요청자의 role + memberCount 반환

**방 수정**
1. `roomUuid`로 Room 조회
2. 현재 사용자가 HOST인지 확인 → 아니면 403
3. 전달된 필드만 업데이트 (null인 필드는 무시)
4. startDate ≤ endDate 검증

**방 삭제**
1. `roomUuid`로 Room 조회
2. 현재 사용자가 HOST인지 확인 → 아니면 403
3. `room.delete()` → `deletedAt = Instant.now()`

### RoomInviteService (초대 + 승인)

**초대 코드 재발급**
1. `roomUuid`로 Room 조회
2. HOST 확인 → 아니면 403
3. `InviteCodeGenerator`로 새 코드 생성
4. `room.regenerateInviteCode(newCode)`
5. 새 코드 반환

**입장 요청 (POST /rooms/join)**
1. `inviteCode`로 Room 조회 (deletedAt IS NULL) → 없으면 404
2. 이미 MEMBER/HOST → 방 정보 반환 (멱등, 200)
3. 이미 PENDING → 대기 상태 반환 (멱등, 202)
4. 신규 → `RoomMember.of(room, user, PENDING)` 저장 → 202

**입장 상태 조회 (GET /rooms/join/status)**
1. `inviteCode`로 Room 조회
2. 현재 사용자의 해당 방 멤버 상태 확인
3. MEMBER/HOST → `approved` + 방 정보 반환
4. PENDING → `pending` 반환
5. 레코드 없음(거절됨) → 404

**입장 승인**
1. HOST 확인
2. 해당 요청의 role을 `PENDING → MEMBER`로 변경

**입장 거절**
1. HOST 확인
2. 해당 `room_member` 레코드 삭제

### 권한 체크 공통 패턴

```java
private RoomMember getHostMember(Room room, Long userId) {
    RoomMember member = roomMemberRepository
        .findByRoomAndUserId(room, userId)
        .orElseThrow(() -> new CustomException(ErrorCode.NOT_ROOM_MEMBER));
    if (member.getRole() != RoomRole.HOST) {
        throw new CustomException(ErrorCode.NOT_ROOM_HOST);
    }
    return member;
}
```

각 서비스에 private 메서드로 둔다.

---

## 7. ErrorCode

| ErrorCode | HttpStatus | 메시지 | 발생 상황 |
|-----------|-----------|--------|-----------|
| `ROOM_NOT_FOUND` | 404 | 존재하지 않는 방입니다 | roomUuid/inviteCode로 조회 실패 또는 삭제된 방 |
| `NOT_ROOM_MEMBER` | 403 | 방의 멤버가 아닙니다 | 멤버가 아닌 사용자가 방 내부 API 접근 |
| `NOT_ROOM_HOST` | 403 | 호스트 권한이 필요합니다 | MEMBER가 수정/삭제/초대코드 재발급/승인/거절 시도 |
| `JOIN_REQUEST_NOT_FOUND` | 404 | 존재하지 않는 입장 요청입니다 | 승인/거절 시 해당 요청이 없음 |
| `INVALID_DATE_RANGE` | 400 | 시작일이 종료일보다 늦을 수 없습니다 | startDate > endDate |
| `ROOM_TITLE_REQUIRED` | 400 | 방 제목은 필수입니다 | title 누락 |

### 멱등 처리 (예외가 아닌 경우)

| 상황 | 처리 |
|------|------|
| 이미 MEMBER/HOST가 초대 코드로 입장 요청 | 방 정보 반환 (200) |
| 이미 PENDING이 초대 코드로 입장 요청 | 대기 상태 반환 (202) |
| 상태 조회 시 이미 MEMBER | `approved` + 방 정보 반환 (200) |

---

## 8. STOMP 연동 참고 (이번 스펙 구현 대상 아님)

방 입장의 전체 흐름에서 Rooms 기능과 STOMP의 접점을 기록한다.

```
초대 코드 입력 → PENDING 등록 → 대기 화면 (새로고침 버튼으로 상태 조회)
→ HOST 승인 → MEMBER 변경 → 클라이언트 새로고침 시 approved 확인
→ 초기 HTTP (데이터 로드 + 멤버 검증) → WS 업그레이드 → STOMP CONNECT → SUBSCRIBE
```

- STOMP WebSocket 업그레이드는 MEMBER/HOST 상태에서만 허용
- PENDING 사용자는 WebSocket 연결 불가
- SUBSCRIBE 성공 시 방 전체에 온라인 유저 목록 브로드캐스트 (PENDING 제외)

---

## 9. ERD 변경 사항

기존 ERD에서 `rooms.id`가 UUID로 정의되어 있으나, 다음과 같이 변경한다:

- `rooms.id` → `BIGINT GENERATED ALWAYS AS IDENTITY` (내부 PK)
- `rooms.uuid` → `UUID NOT NULL UNIQUE` (외부 노출용, 추가 컬럼)
- `room_members.room_id` → `BIGINT FK` (기존 UUID FK에서 변경)
- `room_members.role` → DEFAULT 값을 `'MEMBER'`에서 `'PENDING'`으로 변경하지 않음 (명시적 지정)

이 변경은 구현 시 `docs/ai/erd.md`에 함께 반영한다.
