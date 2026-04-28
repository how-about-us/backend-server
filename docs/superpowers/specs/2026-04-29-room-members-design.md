# 방 멤버 목록 조회 API 설계

Generated on 2026-04-29
Branch: feature/roomMembers

## 목적

방에 참여 중인 멤버 목록을 조회하고, 각 멤버의 실시간 접속 상태를 함께 반환한다.

## API

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
      "joinedAt": "2026-04-20T10:00:00Z"
    }
  ]
}
```

## 접근 제어

- HOST 또는 MEMBER만 조회 가능
- `RoomAuthorizationService.requireActiveMember(roomId, userId)` 재활용

## 조회 대상

- HOST, MEMBER 역할만 반환. PENDING은 제외 (별도 대기 요청 목록 API 존재)

## 신규 파일

| 파일 | 패키지 | 역할 |
|------|--------|------|
| `RoomMemberService` | `rooms/service/` | 멤버 목록 조회 비즈니스 로직 |
| `RoomMemberResult` | `rooms/service/dto/` | 서비스 레이어 반환 DTO |
| `RoomMemberResponse` | `rooms/controller/dto/` | 단일 멤버 응답 DTO |
| `RoomMemberListResponse` | `rooms/controller/dto/` | wrapper DTO |

## 기존 파일 수정

| 파일 | 변경 내용 |
|------|-----------|
| `RoomMemberRepository` | `findByRoom_IdAndRoleIn(UUID, List<RoomRole>)` + `@EntityGraph(attributePaths = "user")` 추가 |
| `RoomController` | `GET /{roomId}/members` 엔드포인트 추가, `RoomMemberService` 주입 |

## 서비스 흐름

```
RoomMemberService.getMembers(roomId, userId):
  1. RoomAuthorizationService.requireActiveMember(roomId, userId)
  2. RoomMemberRepository.findByRoom_IdAndRoleIn(roomId, [HOST, MEMBER])
  3. RoomPresenceService.getOnlineUserIds(roomId)  -- Redis, 실패 시 빈 Set
  4. 매핑 → List<RoomMemberResult> 반환
```

## Redis 장애 처리

- `RoomPresenceService.getOnlineUserIds()` 호출을 try-catch로 감싼다
- Redis 예외 발생 시 빈 Set으로 대체 → 모든 멤버 `isOnline: false`
- 복구 후 STOMP `/presence` 실시간 이벤트로 프론트가 자동 갱신하므로 별도 복구 로직 불필요

## Edge Cases

- 멤버가 0명 (전원 탈퇴): 빈 `members` 배열 반환 (정상 200)
- Redis 조회 실패: 모든 멤버를 `isOnline: false`로 처리

## 재활용 코드

| 컴포넌트 | 용도 |
|----------|------|
| `RoomMemberRepository` | HOST/MEMBER 멤버 조회 |
| `RoomPresenceService` | Redis 접속 유저 Set 조회 |
| `RoomAuthorizationService` | 접근 제어 |
| `RoomRole` | HOST, MEMBER, PENDING enum |

## Post-Implementation

- `features.md`에서 "방 멤버 목록 조회" 항목을 `[x]`로 갱신
- `features.md`에서 "현재 접속 중인 유저 조회" 항목을 `[x]`로 갱신하고, 멤버 목록 API에 접속 상태가 포함됨을 주석으로 남긴다
