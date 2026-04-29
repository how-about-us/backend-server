# 멤버 추방 + 방 나가기 API 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HOST가 멤버를 추방하고, MEMBER가 자발적으로 방을 나갈 수 있는 API를 구현한다.

**Architecture:** `RoomMemberService`에 `kick()`, `leave()` 메서드를 추가하고, 각각 이벤트를 발행하여 `@TransactionalEventListener`로 시스템 메시지 전송 + Redis 접속 상태 정리를 수행한다. 기존 `MemberApprovedEvent` → `MemberApprovedMessageListener` 패턴을 그대로 복제한다.

**Tech Stack:** Spring Boot 4, Java 21, JPA, Redis, MongoDB (채팅), ApplicationEventPublisher, Mockito (테스트)

**Design:** [specs/2026-04-29-kick-leave-design.md](../specs/2026-04-29-kick-leave-design.md)
**Plan Reference:** [plans/20260429-kick-leave-plan.md](../../gstack/plans/20260429-kick-leave-plan.md)

---

## 파일 구조

| Action | Path | 역할 |
|--------|------|------|
| Modify | `common/error/ErrorCode.java` | 에러코드 3개 추가 |
| Create | `realtime/event/MemberKickedEvent.java` | 추방 이벤트 record |
| Create | `realtime/event/MemberLeftEvent.java` | 탈퇴 이벤트 record |
| Modify | `realtime/service/RoomPresenceService.java` | `removeAllSessions()` 메서드 추가 |
| Modify | `rooms/service/RoomMemberService.java` | `kick()`, `leave()` 메서드 추가 |
| Create | `messages/listener/MemberKickedMessageListener.java` | 추방 시스템 메시지 + Redis 정리 |
| Create | `messages/listener/MemberLeftMessageListener.java` | 탈퇴 시스템 메시지 + Redis 정리 |
| Modify | `messages/service/MessageService.java` | 시스템 메시지 메서드 2개 추가 |
| Modify | `rooms/controller/RoomController.java` | 엔드포인트 2개 추가 |
| Modify | `rooms/service/RoomMemberServiceTest.java` (test) | kick/leave 테스트 추가 |
| Create | `messages/listener/MemberKickedMessageListenerTest.java` (test) | 리스너 테스트 |
| Create | `messages/listener/MemberLeftMessageListenerTest.java` (test) | 리스너 테스트 |
| Modify | `realtime/service/RoomPresenceServiceTest.java` (test) | removeAllSessions 테스트 |
| Modify | `docs/ai/features.md` | 구현 완료 표기 |

> **경로 접두사:** `src/main/java/com/howaboutus/backend/` (소스), `src/test/java/com/howaboutus/backend/` (테스트)

---

## Tasks

| # | Task | 파일 | 복잡도 |
|---|------|------|--------|
| 1 | [ErrorCode 추가](2026-04-29-kick-leave/tasks/task-01-error-code.md) | ErrorCode.java | S |
| 2 | [이벤트 Record 생성](2026-04-29-kick-leave/tasks/task-02-event-records.md) | MemberKickedEvent, MemberLeftEvent | S |
| 3 | [RoomPresenceService.removeAllSessions()](2026-04-29-kick-leave/tasks/task-03-presence-remove.md) | RoomPresenceService + Test | S |
| 4 | [RoomMemberService.kick()](2026-04-29-kick-leave/tasks/task-04-kick.md) | RoomMemberService + Test | M |
| 5 | [RoomMemberService.leave()](2026-04-29-kick-leave/tasks/task-05-leave.md) | RoomMemberService + Test | M |
| 6 | [MessageService 시스템 메시지](2026-04-29-kick-leave/tasks/task-06-message-service.md) | MessageService | S |
| 7 | [MemberKickedMessageListener](2026-04-29-kick-leave/tasks/task-07-kicked-listener.md) | Listener + Test | S |
| 8 | [MemberLeftMessageListener](2026-04-29-kick-leave/tasks/task-08-left-listener.md) | Listener + Test | S |
| 9 | [Controller 엔드포인트](2026-04-29-kick-leave/tasks/task-09-controller.md) | RoomController | S |
| 10 | [전체 테스트 + features.md](2026-04-29-kick-leave/tasks/task-10-test-docs.md) | features.md | S |
