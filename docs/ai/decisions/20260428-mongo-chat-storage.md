# 20260428-mongo-chat-storage

## Status

확정

## Context

- 채팅 메시지는 `CHAT`, `AI_REQUEST`, `AI_RESPONSE`, `PLACE_SHARE`, `SYSTEM` 등 타입별 metadata 구조가 달라질 수 있다.
- PostgreSQL 고정 스키마 또는 JSONB를 사용할 수도 있지만, 이 프로젝트에서는 MongoDB 학습 목적도 함께 고려한다.
- 방, 유저, 멤버 권한은 기존 PostgreSQL 도메인을 계속 기준으로 삼는다.

## Decision

- 채팅 메시지는 MongoDB `messages` 컬렉션에 저장한다.
- PostgreSQL은 방/유저/권한의 source of truth로 유지하고, MongoDB 메시지는 `roomId`, `senderId`로 논리 참조한다.
- 클라이언트에는 MongoDB `_id`를 문자열 `id`로 노출하고, 재접속 동기화의 `afterId` cursor로 사용한다.

## Consequences

- 메시지 타입별 metadata를 스키마 변경 없이 확장할 수 있다.
- RDB FK 제약으로 메시지 정합성을 강제하지 않으므로 서비스 레이어 권한 확인과 조회 조건이 중요하다.
- 기존 PostgreSQL `messages` 테이블 기준 문서는 MongoDB 컬렉션 기준으로 갱신해야 한다.

## Related Docs

- `docs/ai/features.md`
- `docs/ai/erd.md`
