# Plan: 방 멤버 목록 조회 API 구현

Generated on 2026-04-28
Branch: feature/roomMembers
Design: [specs/20260428-room-members-design.md](../specs/20260428-room-members-design.md)

## Premises

1. HOST/MEMBER만 반환, PENDING 제외 (별도 대기 요청 목록 API 존재)
2. Redis connected_users로 접속 상태를 응답에 포함. Redis 조회 실패 시 모든 멤버를 offline으로 처리한다
3. REST GET으로 초기 로드, STOMP /presence로 실시간 갱신 (기존 프로젝트 패턴)
4. 사용자 프로필 정보(닉네임, 프로필 이미지) 포함
5. HOST 또는 MEMBER만 조회 가능 (기존 접근 제어 패턴)

## API 설계

```
GET /rooms/{roomId}/members

Response 200:
{
  "members": [
    {
      "userId": 1,
      "nickname": "홍길동",
      "profileImageUrl": "https://...",
      "role": "HOST",
      "isOnline": true,
      "joinedAt": "2026-04-20T10:00:00"
    },
    {
      "userId": 2,
      "nickname": "김철수",
      "profileImageUrl": null,
      "role": "MEMBER",
      "isOnline": false,
      "joinedAt": "2026-04-21T14:30:00"
    }
  ]
}
```

## 구현 포인트

### 1. Service

`RoomMemberService`를 신규 생성한다 (기존 `RoomAuthorizationService` 등과 일관된 서비스 분리 패턴).

- `RoomAuthorizationService.requireActiveMember(roomId, userId)` 호출로 접근 제어
- `RoomMemberRepository.findByRoom_IdAndRoleIn(roomId, List.of(HOST, MEMBER))` + EntityGraph로 User JOIN
- `RoomPresenceService.getOnlineUserIds(roomId)` 결과를 Set으로 받아 각 멤버의 `isOnline` 매핑

### 2. DTO

- `RoomMemberResult` (service layer) — userId, nickname, profileImageUrl, role, isOnline, joinedAt
- `RoomMemberResponse` (controller layer) — 단일 멤버 응답
- `RoomMemberListResponse` (controller layer) — `List<RoomMemberResponse>`를 감싸는 wrapper DTO

Coding Convention: DTO는 `service/dto/` 패키지에 별도 파일로 정의 (서비스 클래스 내부 record 선언 금지).

### 3. Controller

`RoomController`에 엔드포인트 추가:

```java
@GetMapping("/rooms/{roomId}/members")
public ResponseEntity<RoomMemberListResponse> getMembers(
    @PathVariable UUID roomId,
    @AuthenticationPrincipal CustomUserDetails userDetails
)
```

### 4. Repository

`findByRoom_IdAndRoleIn(roomId, roles)` 메서드를 추가한다. 단일 쿼리로 HOST/MEMBER를 한 번에 조회하는 것이 단순하고 성능상 유리.

## 재활용 코드

| 컴포넌트 | 경로 | 용도 |
|----------|------|------|
| `RoomMemberRepository` | rooms/repository/ | HOST/MEMBER 멤버 조회 |
| `RoomPresenceService` | realtime/service/ | Redis 접속 유저 Set 조회 |
| `RoomAuthorizationService` | rooms/service/ | 접근 제어 (requireActiveMember) |
| `RoomRole` | rooms/entity/ | HOST, MEMBER, PENDING enum |

## 성공 기준

- HOST/MEMBER 역할이 정확히 반환됨
- Redis 접속 상태(online/offline)가 실시간 상태와 일치
- PENDING 멤버는 제외됨
- 비멤버 접근 시 403 반환
- 기존 bookmarks/schedules 조회와 일관된 패턴

## Edge Cases

- 멤버가 0명인 경우 (전원 탈퇴): 빈 `members` 배열 반환 (정상 응답)
- Redis 조회 실패: 모든 멤버를 `isOnline: false`로 처리

## Post-Implementation

- `features.md`에서 "방 멤버 목록 조회" 항목을 `[x]`로 갱신
- `features.md`에서 "현재 접속 중인 유저 조회" 항목을 `[x]`로 갱신하고, 멤버 목록 API에 접속 상태가 포함됨을 주석으로 남긴다

## Dependencies

- `RoomMemberRepository` (구현 완료)
- `RoomPresenceService` (구현 완료)
- `RoomAuthorizationService` (구현 완료)
- 블로커 없음
