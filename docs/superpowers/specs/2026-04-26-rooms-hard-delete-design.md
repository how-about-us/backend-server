# Rooms Hard Delete 전환 디자인

## 개요

Rooms 도메인의 삭제 정책을 soft delete(`deletedAt` 타임스탬프)에서 hard delete(DB row 물리 삭제)로 전환한다.

## 배경

기존에는 복구 가능성을 고려해 soft delete를 적용했으나, 현재 복구 기능 요구사항이 없고 soft delete로 인해 모든 조회 쿼리에 `deletedAt IS NULL` 조건이 필요해 불필요한 복잡도가 발생하고 있다.

## 삭제 전략

- **방식**: 서비스 레이어 명시적 삭제 (접근법 A)
- **순서**: RoomMember 먼저 삭제 → Room 삭제
- **권한**: HOST만 삭제 가능 (기존 정책 유지)
- **이유**: Room → RoomMember 단방향(`@ManyToOne`) 관계를 유지하기 위해 JPA Cascade 대신 서비스 레이어에서 명시적으로 처리. `deleteByRoom_Id`는 단일 DELETE 쿼리로 실행되어 성능상 이점이 있다.

## 변경 범위

### 1. Room 엔티티

- `deletedAt` 필드 제거
- `delete()` 메서드 제거
- `isDeleted()` 메서드 제거

### 2. RoomRepository

| 기존 | 변경 |
|------|------|
| `findByIdAndDeletedAtIsNull(UUID id)` | 제거 — `JpaRepository.findById()` 사용 |
| `findByInviteCodeAndDeletedAtIsNull(String code)` | `findByInviteCode(String code)` |

### 3. RoomMemberRepository

| 기존 | 변경 |
|------|------|
| (없음) | `deleteByRoom_Id(UUID roomId)` 추가 |
| `findByUser_IdAndRoleInAndRoom_DeletedAtIsNullOrderByJoinedAtDesc(...)` | `findByUser_IdAndRoleInOrderByJoinedAtDesc(...)` |
| `findByUser_IdAndRoleInAndRoom_DeletedAtIsNullAndJoinedAtBeforeOrderByJoinedAtDesc(...)` | `findByUser_IdAndRoleInAndJoinedAtBeforeOrderByJoinedAtDesc(...)` |

### 4. RoomService

- `delete()`: `room.delete()` 대신 `roomMemberRepository.deleteByRoom_Id(roomId)` + `roomRepository.delete(room)` 호출
- `getActiveRoom()`: `findByIdAndDeletedAtIsNull()` → `findById()` 사용
- `getMyRooms()`: 변경된 RoomMemberRepository 메서드명 반영

### 5. RoomInviteService

- `getActiveRoom()`: `findByIdAndDeletedAtIsNull()` → `findById()` 사용
- `requestJoin()`: `findByInviteCodeAndDeletedAtIsNull()` → `findByInviteCode()` 사용

### 6. 테스트

- `RoomServiceTest`: soft delete mock/assertion을 hard delete 방식으로 수정
- `RoomInviteServiceTest`: repository mock 메서드명 변경 반영
- `RoomControllerTest`: 통합 테스트 내 soft delete 관련 부분 수정 (해당 시)

### 7. DB 스키마

- `rooms` 테이블의 `deleted_at` 컬럼 제거 필요
- Hibernate DDL auto로 관리 중이면 엔티티 변경 시 자동 반영
- 프로덕션 환경에서는 별도 마이그레이션 스크립트 필요할 수 있음

## 영향받지 않는 부분

- 삭제 권한 체크(HOST만 삭제 가능) — 기존 로직 그대로 유지
- Room 생성, 수정, 조회 API — `deletedAt IS NULL` 조건만 제거되고 비즈니스 로직은 동일
- 북마크 등 다른 도메인 — Room 삭제 시 orphan 데이터 처리는 별도 이슈
