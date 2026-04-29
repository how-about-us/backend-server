# Design: 방장 위임 (Host Delegation)

> 상세 설계는 gstack office-hours 설계 문서를 참조한다.
> 경로: `~/.gstack/projects/how-about-us-backend-server/parkjuyeong-feature-roomMembers-design-20260429-132935.md`

## Summary

HOST가 특정 MEMBER에게 방장 권한을 위임하는 기능. 한 트랜잭션에서 role swap (HOST→MEMBER, MEMBER→HOST) 후 시스템 메시지 + WebSocket 이벤트 브로드캐스트.

## API

| Method | Endpoint | 권한 | 설명 |
|--------|----------|------|------|
| PATCH | `/rooms/{roomId}/host` | HOST | 방장 위임. body: `{"targetUserId": Long}` |

### Response Codes

```
200 OK             — 위임 성공
403 Forbidden      — NOT_ROOM_HOST (HOST가 아닌 사용자)
400 Bad Request    — DELEGATE_TARGET_NOT_MEMBER (대상이 MEMBER가 아님)
400 Bad Request    — CANNOT_DELEGATE_TO_SELF (자기 자신에게 위임)
404 Not Found      — 해당 userId의 멤버가 없음 (JOIN_REQUEST_NOT_FOUND 재활용)
```

## 구현 범위

1. ErrorCode 2개 추가
2. `RoomMember` 엔티티에 `promoteToHost()`, `demoteToMember()` 메서드 추가
3. `RoomMemberService.delegateHost()` 메서드 추가
4. `HostDelegatedEvent` record 추가
5. `HostDelegatedMessageListener` 추가 (시스템 메시지 + WebSocket 브로드캐스트)
6. `MessageService.sendHostDelegatedSystemMessage()` 추가
7. `RoomController` PATCH 엔드포인트 추가
8. 단위 테스트

## 참조 패턴

kick/leave 구현 계획: `docs/gstack/plans/20260429-kick-leave-plan.md`

## Premises

1. HOST가 MEMBER를 지목하여 명시적으로 위임 (자동 승계 없음)
2. 한 트랜잭션에서 role swap
3. 위임 후 기존 HOST는 MEMBER로 남음 (leave 별도 호출)
4. 위임 시 시스템 메시지 브로드캐스트
5. MEMBER가 없으면 위임 불가

## Success Criteria

- HOST가 특정 MEMBER에게 방장 권한을 위임할 수 있다
- 위임 후 기존 HOST는 MEMBER, 대상은 HOST가 된다
- HOST 혼자인 방에서는 위임할 수 없다
- 위임 대상은 MEMBER만 가능. PENDING, 비멤버, 자기 자신 불가
- 위임 시 시스템 메시지가 채팅에 표시된다
- 위임 시 WebSocket 이벤트로 모든 클라이언트가 새 HOST를 인지한다
- 위임 후 기존 HOST가 leave()로 방을 나갈 수 있다
