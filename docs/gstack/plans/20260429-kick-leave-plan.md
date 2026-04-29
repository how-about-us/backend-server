``# Plan: 멤버 추방 + 방 나가기 API 구현

Generated on 2026-04-29
Branch: feature/roomMembers
Design: [specs/20260429-kick-leave-design.md](../specs/20260429-kick-leave-design.md)

## Eng Review 결정사항 (2026-04-29)

| # | 결정 | 선택 |
|---|------|------|
| D1 | Kick endpoint path variable | `users.id` 사용 (`DELETE /rooms/{roomId}/members/{userId}`) |
| D2 | PENDING 멤버 추방 시도 | MEMBER만 kick 가능. PENDING/HOST는 별도 에러코드 |
| D4 | 이벤트/리스너 구조 | 2개 유지 (MemberKickedEvent + MemberLeftEvent) |
| D5 | 추방/탈퇴 후 채팅 접근 | 접근 불가 유지 (requireActiveMember 자동 차단) |
| D6 | Redis cleanup 실패 처리 | try-catch 추가 (시스템 메시지 전송 차단 방지) |

## API 설계

### 멤버 추방

```
DELETE /rooms/{roomId}/members/{userId}
Authorization: HOST only

Response:
  204 No Content — 추방 성공
  403 Forbidden  — NOT_ROOM_HOST (HOST가 아닌 사용자)
  403 Forbidden  — CANNOT_KICK_HOST (HOST를 추방하려는 경우)
  400 Bad Request — KICK_TARGET_NOT_MEMBER (PENDING 등 MEMBER가 아닌 대상)
  404 Not Found  — 해당 userId의 멤버가 없음
```

### 방 나가기

```
DELETE /rooms/{roomId}/members/me
Authorization: MEMBER only

Response:
  204 No Content — 나가기 성공
  403 Forbidden  — CANNOT_LEAVE_AS_HOST (HOST는 나갈 수 없음)
  403 Forbidden  — NOT_ROOM_MEMBER (멤버가 아닌 사용자)
```

## 구현 포인트

### 1. ErrorCode 추가

`ErrorCode` enum에 3개 추가:

```java
CANNOT_KICK_HOST(HttpStatus.FORBIDDEN, "호스트는 추방할 수 없습니다"),
KICK_TARGET_NOT_MEMBER(HttpStatus.BAD_REQUEST, "추방 대상이 멤버가 아닙니다"),
CANNOT_LEAVE_AS_HOST(HttpStatus.FORBIDDEN, "호스트는 방을 나갈 수 없습니다"),
```

### 2. RoomMemberService — kick(), leave() 메서드 추가

```
kick(roomId, targetUserId, hostUserId):
  1. roomAuthorizationService.requireHost(roomId, hostUserId)
  2. roomMemberRepository.findByRoom_IdAndUser_Id(roomId, targetUserId)
     → 없으면 404
  3. target.getRole() == HOST → CANNOT_KICK_HOST
  4. target.getRole() != MEMBER → KICK_TARGET_NOT_MEMBER
  5. roomMemberRepository.delete(target)
  6. eventPublisher.publishEvent(new MemberKickedEvent(...))

leave(roomId, userId):
  1. roomAuthorizationService.requireActiveMember(roomId, userId)
     → 반환된 RoomMember의 role 확인
  2. member.getRole() == HOST → CANNOT_LEAVE_AS_HOST
  3. roomMemberRepository.delete(member)
  4. eventPublisher.publishEvent(new MemberLeftEvent(...))
```

ApplicationEventPublisher 의존성 추가 필요.

### 3. Event Records (새 파일 2개)

`realtime/event/` 패키지에 생성:

```java
// MemberKickedEvent.java
public record MemberKickedEvent(
    UUID roomId,
    long kickedUserId,
    String nickname,
    String profileImageUrl
) {}

// MemberLeftEvent.java
public record MemberLeftEvent(
    UUID roomId,
    long leftUserId,
    String nickname,
    String profileImageUrl
) {}
```

### 4. Message Listeners (새 파일 2개)

`messages/listener/` 패키지에 생성. 기존 `MemberApprovedMessageListener` 패턴 복제:

```java
@Component
@RequiredArgsConstructor
public class MemberKickedMessageListener {
    private final MessageService messageService;
    private final RoomPresenceService roomPresenceService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(MemberKickedEvent event) {
        removePresenceSafe(event.roomId(), event.kickedUserId());
        messageService.sendMemberKickedSystemMessage(
            event.roomId(), event.kickedUserId(),
            event.nickname(), event.profileImageUrl());
    }

    private void removePresenceSafe(UUID roomId, long userId) {
        try {
            roomPresenceService.removeAllSessions(roomId, userId);
        } catch (Exception e) {
            log.warn("Redis 접속 상태 제거 실패: roomId={}, userId={}", roomId, userId, e);
        }
    }
}
```

`MemberLeftMessageListener`도 동일 구조 (메서드명만 다름).

### 5. MessageService — 시스템 메시지 메서드 2개 추가

기존 `sendMemberJoinedSystemMessage` 패턴 복제:

```java
sendMemberKickedSystemMessage(roomId, userId, nickname, profileImageUrl)
  → content: "{nickname}님이 방에서 내보내졌습니다"
  → metadata.eventType: "MEMBER_KICKED"

sendMemberLeftSystemMessage(roomId, userId, nickname, profileImageUrl)
  → content: "{nickname}님이 방을 나갔습니다"
  → metadata.eventType: "MEMBER_LEFT"
```

### 6. RoomPresenceService — removeAllSessions() 추가

```java
public void removeAllSessions(UUID roomId, Long userId) {
    redisTemplate.delete(userSessionsKey(roomId, userId));
    redisTemplate.opsForSet().remove(connectedUsersKey(roomId), String.valueOf(userId));
}
```

기존 `disconnect()`와 동일한 비원자적 접근. 기존 TODO (Lua Script)와 같은 수준의 리스크.

### 7. Controller 엔드포인트 추가

`RoomController`에 2개 엔드포인트:

```java
@DeleteMapping("/{roomId}/members/{userId}")
public ResponseEntity<Void> kickMember(
    @AuthenticationPrincipal Long hostUserId,
    @PathVariable UUID roomId,
    @PathVariable Long userId
) {
    roomMemberService.kick(roomId, userId, hostUserId);
    return ResponseEntity.noContent().build();
}

@DeleteMapping("/{roomId}/members/me")
public ResponseEntity<Void> leaveRoom(
    @AuthenticationPrincipal Long userId,
    @PathVariable UUID roomId
) {
    roomMemberService.leave(roomId, userId);
    return ResponseEntity.noContent().build();
}
```

**주의:** Spring은 `/members/me`와 `/members/{userId}`에서 "me"를 Long으로 파싱 시도할 수 있음. `/members/me`가 먼저 매칭되도록 메서드 순서를 확인하거나, 명시적 경로 우선순위를 설정해야 함.

## 재활용 코드

| 컴포넌트 | 경로 | 용도 |
|----------|------|------|
| `RoomAuthorizationService` | rooms/service/ | requireHost, requireActiveMember |
| `RoomMemberRepository` | rooms/repository/ | findByRoom_IdAndUser_Id, delete |
| `RoomPresenceService` | realtime/service/ | removeAllSessions (신규 메서드) |
| `MessageService` | messages/service/ | 시스템 메시지 저장 + 브로드캐스트 (신규 메서드) |
| `MemberApprovedEvent` 패턴 | realtime/event/ | 이벤트 record 구조 참고 |
| `MemberApprovedMessageListener` 패턴 | messages/listener/ | AFTER_COMMIT 리스너 구조 참고 |

## 테스트 계획

### RoomMemberServiceTest (확장)

| 테스트 | 검증 |
|--------|------|
| kick 성공 | delete 호출 + 이벤트 발행 |
| kick — HOST 추방 시도 | CANNOT_KICK_HOST 예외 |
| kick — PENDING 추방 시도 | KICK_TARGET_NOT_MEMBER 예외 |
| kick — 존재하지 않는 userId | 404 예외 |
| kick — HOST가 아닌 사용자가 시도 | NOT_ROOM_HOST 예외 |
| kick — 자기 자신 추방 시도 | CANNOT_KICK_HOST 예외 (role 체크로 차단) |
| leave 성공 | delete 호출 + 이벤트 발행 |
| leave — HOST 나가기 시도 | CANNOT_LEAVE_AS_HOST 예외 |
| leave — 비멤버 나가기 시도 | NOT_ROOM_MEMBER 예외 |

### MemberKickedMessageListenerTest (신규)

| 테스트 | 검증 |
|--------|------|
| 이벤트 처리 | messageService.sendMemberKickedSystemMessage 호출 확인 |
| Redis 실패 시 | 시스템 메시지 정상 전송 확인 |

### MemberLeftMessageListenerTest (신규)

| 테스트 | 검증 |
|--------|------|
| 이벤트 처리 | messageService.sendMemberLeftSystemMessage 호출 확인 |
| Redis 실패 시 | 시스템 메시지 정상 전송 확인 |

### RoomPresenceServiceTest (확장 또는 신규)

| 테스트 | 검증 |
|--------|------|
| removeAllSessions — 세션 있는 유저 | Redis 키 삭제 + connected_users에서 제거 |
| removeAllSessions — 세션 없는 유저 | 에러 없이 정상 처리 |

## Edge Cases

- HOST가 자기 자신을 추방 → role 체크(HOST)로 자동 차단
- 이중 추방 (동일 유저 2번 kick) → 두 번째 요청에서 findByRoom_IdAndUser_Id 결과 없음 → 404
- 추방 직후 재입장 → 기존 PENDING 플로우 그대로 작동
- Redis 장애 중 추방 → try-catch로 시스템 메시지는 정상 전송, 접속 상태는 스테일

## Post-Implementation

- `docs/ai/features.md`에서 "멤버 추방" 항목을 `[x]`로 갱신
- `docs/ai/features.md`에서 "방 나가기" 항목을 `[x]`로 갱신

## Dependencies

- `RoomMemberRepository` (구현 완료)
- `RoomPresenceService` (구현 완료, removeAllSessions 추가 필요)
- `MessageService` (구현 완료, 시스템 메시지 메서드 추가 필요)
- `RoomAuthorizationService` (구현 완료)
- 블로커 없음
