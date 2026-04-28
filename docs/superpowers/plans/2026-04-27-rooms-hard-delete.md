# Rooms Hard Delete 전환 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Room 삭제 정책을 soft delete(`deletedAt`)에서 hard delete(DB row 물리 삭제)로 전환한다.

**Architecture:** Room 엔티티에서 `deletedAt` 필드와 관련 메서드를 제거하고, Repository의 `DeletedAtIsNull` 조건 쿼리를 단순 쿼리로 교체한다. 서비스 레이어에서 RoomMember를 먼저 삭제한 후 Room을 물리 삭제하는 명시적 삭제 순서를 적용한다.

**Tech Stack:** Spring Boot 4.0.5, Java 21, Spring Data JPA, PostgreSQL 17, Mockito/JUnit 5

---

## File Map

| 액션 | 파일 | 변경 내용 |
|------|------|-----------|
| Modify | `src/main/java/com/howaboutus/backend/rooms/entity/Room.java` | `deletedAt` 필드, `delete()`, `isDeleted()` 제거 |
| Modify | `src/main/java/com/howaboutus/backend/rooms/repository/RoomRepository.java` | `findByIdAndDeletedAtIsNull` 제거, `findByInviteCodeAndDeletedAtIsNull` → `findByInviteCode` |
| Modify | `src/main/java/com/howaboutus/backend/rooms/repository/RoomMemberRepository.java` | `deleteByRoom_Id` 추가, 두 메서드 이름에서 `Room_DeletedAtIsNull` 제거 |
| Modify | `src/main/java/com/howaboutus/backend/rooms/service/RoomService.java` | `delete()` 로직 변경, `getActiveRoom()` 변경, `getMyRooms()` 메서드명 반영 |
| Modify | `src/main/java/com/howaboutus/backend/rooms/service/RoomInviteService.java` | `getActiveRoom()` 변경, `requestJoin()` 메서드명 반영 |
| Modify | `src/test/java/com/howaboutus/backend/rooms/service/RoomServiceTest.java` | soft delete mock/assertion → hard delete 방식으로 수정 |
| Modify | `src/test/java/com/howaboutus/backend/rooms/service/RoomInviteServiceTest.java` | repository mock 메서드명 변경 반영 |
| Modify | `docs/ai/erd.md` | `rooms` 테이블에서 `deleted_at` 컬럼 제거, 설계 포인트 갱신 |

---

### Task 1: Room 엔티티에서 soft delete 관련 코드 제거

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/entity/Room.java:9,46-84`

- [ ] **Step 1: `deletedAt` 필드와 관련 메서드 제거**

`Room.java`에서 다음 세 부분을 제거한다:

```java
// 제거할 import (9행)
import java.time.Instant;

// 제거할 필드 (46-47행)
@Column(name = "deleted_at")
private Instant deletedAt;

// 제거할 메서드 (78-84행)
public void delete() {
    this.deletedAt = Instant.now();
}

public boolean isDeleted() {
    return this.deletedAt != null;
}
```

변경 후 `Room.java`에는 `deletedAt` 관련 코드가 전혀 없어야 한다.

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava 2>&1 | head -30`
Expected: 컴파일 에러 발생 (다른 파일에서 `deletedAt`, `isDeleted()`, `delete()` 참조). 이 시점에서는 정상이다.

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/entity/Room.java
git commit -m "refactor: Room 엔티티에서 deletedAt 필드 및 soft delete 메서드 제거"
```

---

### Task 2: RoomRepository에서 soft delete 조건 쿼리 제거

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/repository/RoomRepository.java:10-13`

- [ ] **Step 1: Repository 메서드 변경**

`RoomRepository.java` 전체를 다음으로 교체한다:

```java
package com.howaboutus.backend.rooms.repository;

import com.howaboutus.backend.rooms.entity.Room;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    Optional<Room> findByInviteCode(String inviteCode);
}
```

기존 `findByIdAndDeletedAtIsNull`은 제거 — `JpaRepository.findById()`를 직접 사용한다.
기존 `findByInviteCodeAndDeletedAtIsNull` → `findByInviteCode`로 이름을 단순화한다.

- [ ] **Step 2: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/repository/RoomRepository.java
git commit -m "refactor: RoomRepository에서 deletedAt 조건 쿼리 제거"
```

---

### Task 3: RoomMemberRepository에 deleteByRoom_Id 추가 및 메서드명 정리

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/repository/RoomMemberRepository.java:13-31`

- [ ] **Step 1: Repository 메서드 변경**

`RoomMemberRepository.java` 전체를 다음으로 교체한다:

```java
package com.howaboutus.backend.rooms.repository;

import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    Optional<RoomMember> findByRoom_IdAndUser_Id(UUID roomId, Long userId);

    @EntityGraph(attributePaths = "user")
    List<RoomMember> findByRoom_IdAndRole(UUID roomId, RoomRole role);

    Optional<RoomMember> findByIdAndRoom_Id(Long id, UUID roomId);

    @EntityGraph(attributePaths = "room")
    List<RoomMember> findByUser_IdAndRoleInOrderByJoinedAtDesc(
            Long userId, List<RoomRole> roles, Pageable pageable);

    @EntityGraph(attributePaths = "room")
    List<RoomMember> findByUser_IdAndRoleInAndJoinedAtBeforeOrderByJoinedAtDesc(
            Long userId, List<RoomRole> roles, Instant cursor, Pageable pageable);

    long countByRoom_IdAndRoleIn(UUID roomId, List<RoomRole> roles);

    void deleteByRoom_Id(UUID roomId);
}
```

변경 사항:
- `findByUser_IdAndRoleInAndRoom_DeletedAtIsNullOrderByJoinedAtDesc` → `findByUser_IdAndRoleInOrderByJoinedAtDesc`
- `findByUser_IdAndRoleInAndRoom_DeletedAtIsNullAndJoinedAtBeforeOrderByJoinedAtDesc` → `findByUser_IdAndRoleInAndJoinedAtBeforeOrderByJoinedAtDesc`
- `deleteByRoom_Id(UUID roomId)` 신규 추가

- [ ] **Step 2: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/repository/RoomMemberRepository.java
git commit -m "refactor: RoomMemberRepository soft delete 조건 제거 및 deleteByRoom_Id 추가"
```

---

### Task 4: RoomService hard delete 로직으로 전환

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/service/RoomService.java:17,77-81,83-97,124-127`

- [ ] **Step 1: import 정리**

`RoomService.java`에서 사용하지 않는 import를 제거한다:

```java
// 제거 (17행) — getMyRooms에서 Instant cursor 파라미터로 여전히 사용하므로 유지 확인
// Instant는 getMyRooms 파라미터에서 사용하므로 유지한다. 제거할 import 없음.
```

- [ ] **Step 2: `delete()` 메서드 변경**

`RoomService.java`의 `delete()` 메서드(77-81행)를 다음으로 교체한다:

```java
@Transactional
public void delete(UUID roomId, Long userId) {
    Room room = getActiveRoom(roomId);
    roomAuthorizationService.requireHost(roomId, userId);
    roomMemberRepository.deleteByRoom_Id(roomId);
    roomRepository.delete(room);
}
```

핵심: `room.delete()` (soft delete) 대신 RoomMember를 먼저 물리 삭제한 후 Room을 물리 삭제한다.

- [ ] **Step 3: `getActiveRoom()` 메서드 변경**

`RoomService.java`의 `getActiveRoom()` 메서드(124-127행)를 다음으로 교체한다:

```java
private Room getActiveRoom(UUID roomId) {
    return roomRepository.findById(roomId)
            .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
}
```

- [ ] **Step 4: `getMyRooms()` 메서드의 repository 호출 변경**

`RoomService.java`의 `getMyRooms()` 메서드(83-116행) 내에서 repository 호출 부분만 변경한다:

```java
if (cursor == null) {
    members = roomMemberRepository
            .findByUser_IdAndRoleInOrderByJoinedAtDesc(
                    userId, ACTIVE_ROLES, pageable);
} else {
    members = roomMemberRepository
            .findByUser_IdAndRoleInAndJoinedAtBeforeOrderByJoinedAtDesc(
                    userId, ACTIVE_ROLES, cursor, pageable);
}
```

나머지 로직(페이지네이션, summaries 변환)은 변경 없음.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/service/RoomService.java
git commit -m "refactor: RoomService 삭제 로직을 hard delete로 전환"
```

---

### Task 5: RoomInviteService에서 soft delete 조건 제거

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/service/RoomInviteService.java:49,137-140`

- [ ] **Step 1: `requestJoin()` 메서드의 repository 호출 변경**

`RoomInviteService.java`의 `requestJoin()` 메서드(49행)에서:

```java
// 변경 전
Room room = roomRepository.findByInviteCodeAndDeletedAtIsNull(inviteCode)
        .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

// 변경 후
Room room = roomRepository.findByInviteCode(inviteCode)
        .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
```

- [ ] **Step 2: `getActiveRoom()` 메서드 변경**

`RoomInviteService.java`의 `getActiveRoom()` 메서드(137-140행)를 다음으로 교체한다:

```java
private Room getActiveRoom(UUID roomId) {
    return roomRepository.findById(roomId)
            .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL — 모든 프로덕션 코드가 정상 컴파일된다.

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/service/RoomInviteService.java
git commit -m "refactor: RoomInviteService에서 soft delete 조건 제거"
```

---

### Task 6: RoomServiceTest를 hard delete 방식으로 수정

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/rooms/service/RoomServiceTest.java`

- [ ] **Step 1: `deleteRoomSucceeds` 테스트 수정**

`RoomServiceTest.java`의 `deleteRoomSucceeds()` 테스트(281-298행)를 다음으로 교체한다:

```java
@Test
@DisplayName("HOST가 방을 삭제하면 RoomMember와 Room이 물리 삭제된다")
void deleteRoomSucceeds() {
    UUID roomId = UUID.randomUUID();
    Long userId = 1L;
    Room room = Room.create("부산 여행", "부산", null, null, "aB3xK9mQ2w", userId);
    ReflectionTestUtils.setField(room, "id", roomId);

    User user = User.ofGoogle("google-id", "test@test.com", "테스터", null);
    ReflectionTestUtils.setField(user, "id", userId);
    RoomMember hostMember = RoomMember.of(room, user, RoomRole.HOST);

    given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)).willReturn(Optional.of(hostMember));

    roomService.delete(roomId, userId);

    verify(roomMemberRepository).deleteByRoom_Id(roomId);
    verify(roomRepository).delete(room);
}
```

핵심 변경: `assertThat(room.isDeleted()).isTrue()` → `verify(roomMemberRepository).deleteByRoom_Id(roomId)` + `verify(roomRepository).delete(room)`.

- [ ] **Step 2: `deleteRoomThrowsWhenNotHost` 테스트 수정**

`RoomServiceTest.java`의 `deleteRoomThrowsWhenNotHost()` 테스트(300-319행)에서 mock 메서드명만 변경한다:

```java
@Test
@DisplayName("MEMBER가 방을 삭제하면 NOT_ROOM_HOST 예외")
void deleteRoomThrowsWhenNotHost() {
    UUID roomId = UUID.randomUUID();
    Long userId = 2L;
    Room room = Room.create("부산 여행", "부산", null, null, "aB3xK9mQ2w", 1L);
    ReflectionTestUtils.setField(room, "id", roomId);

    User user = User.ofGoogle("google-id", "member@test.com", "멤버", null);
    ReflectionTestUtils.setField(user, "id", userId);
    RoomMember memberRole = RoomMember.of(room, user, RoomRole.MEMBER);

    given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)).willReturn(Optional.of(memberRole));

    assertThatThrownBy(() -> roomService.delete(roomId, userId))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_ROOM_HOST);
}
```

- [ ] **Step 3: `findByIdAndDeletedAtIsNull` mock을 `findById`로 일괄 변경**

파일 내 모든 `findByIdAndDeletedAtIsNull` 호출을 `findById`로 교체한다. 해당 테스트 목록:
- `getDetailReturnsRoomInfoWithRoleAndMemberCount` (104행)
- `getDetailThrowsWhenRoomDeleted` (120행)
- `getDetailThrowsWhenNotMember` (135행)
- `getDetailThrowsWhenPendingMember` (156행)
- `updateRoomReturnsUpdatedResult` (191행)
- `updateRoomThrowsWhenPartialDateMakesRangeInvalid` (220행)
- `updateRoomThrowsWhenPartialStartDateMakesRangeInvalid` (245행)
- `updateRoomThrowsWhenNotHost` (269행)

각 위치에서:
```java
// 변경 전
given(roomRepository.findByIdAndDeletedAtIsNull(roomId)).willReturn(...)
// 변경 후
given(roomRepository.findById(roomId)).willReturn(...)
```

- [ ] **Step 4: `getMyRooms` 관련 테스트의 repository 메서드명 변경**

3개 테스트에서 mock 메서드명을 변경한다:

```java
// 변경 전
findByUser_IdAndRoleInAndRoom_DeletedAtIsNullOrderByJoinedAtDesc(...)
// 변경 후
findByUser_IdAndRoleInOrderByJoinedAtDesc(...)

// 변경 전
findByUser_IdAndRoleInAndRoom_DeletedAtIsNullAndJoinedAtBeforeOrderByJoinedAtDesc(...)
// 변경 후
findByUser_IdAndRoleInAndJoinedAtBeforeOrderByJoinedAtDesc(...)
```

해당 테스트:
- `getMyRoomsWithoutCursorReturnsLatest` (345행)
- `getMyRoomsWithCursorReturnsBefore` (371행)
- `getMyRoomsHasNextWhenMoreExists` (396행)

- [ ] **Step 5: 테스트 실행**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomServiceTest" --info 2>&1 | tail -20`
Expected: 모든 테스트 PASSED

- [ ] **Step 6: 커밋**

```bash
git add src/test/java/com/howaboutus/backend/rooms/service/RoomServiceTest.java
git commit -m "test: RoomServiceTest를 hard delete 방식으로 수정"
```

---

### Task 7: RoomInviteServiceTest에서 repository mock 메서드명 변경

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/rooms/service/RoomInviteServiceTest.java`

- [ ] **Step 1: `findByIdAndDeletedAtIsNull` → `findById`로 일괄 변경**

파일 내 모든 `findByIdAndDeletedAtIsNull` 호출을 `findById`로 교체한다. 해당 테스트:
- `regenerateInviteCodeSuccess` (74행)
- `regenerateInviteCodeThrowsWhenNotHost` (88행)
- `getJoinStatusReturnsPending` (171행)
- `getJoinStatusReturnsApproved` (185행)
- `getJoinStatusThrowsWhenRejected` (199행)
- `approveChangesRoleToMember` (220행)
- `rejectDeletesMember` (239행)
- `approveThrowsWhenRequestNotFound` (253행)
- `approveThrowsWhenNotHost` (268행)
- `getJoinRequestsReturnsPendingMembers` (288행)

```java
// 변경 전
given(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).willReturn(Optional.of(room));
// 변경 후
given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
```

- [ ] **Step 2: `findByInviteCodeAndDeletedAtIsNull` → `findByInviteCode`로 변경**

해당 테스트:
- `requestJoinCreatesPendingMember` (101행)
- `requestJoinReturnsAlreadyMemberWhenMember` (119행)
- `requestJoinReturnsPendingWhenAlreadyPending` (138행)
- `requestJoinThrowsWhenInvalidCode` (153행)

```java
// 변경 전
given(roomRepository.findByInviteCodeAndDeletedAtIsNull("aB3xK9mQ2w")).willReturn(...)
// 변경 후
given(roomRepository.findByInviteCode("aB3xK9mQ2w")).willReturn(...)
```

- [ ] **Step 3: 테스트 실행**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomInviteServiceTest" --info 2>&1 | tail -20`
Expected: 모든 테스트 PASSED

- [ ] **Step 4: 커밋**

```bash
git add src/test/java/com/howaboutus/backend/rooms/service/RoomInviteServiceTest.java
git commit -m "test: RoomInviteServiceTest의 repository mock 메서드명을 hard delete에 맞게 수정"
```

---

### Task 8: 전체 테스트 실행 및 통합 확인

**Files:**
- Test: `src/test/java/com/howaboutus/backend/rooms/controller/RoomControllerTest.java` (변경 없음 확인)

- [ ] **Step 1: RoomControllerTest 확인**

`RoomControllerTest`는 서비스를 `@MockitoBean`으로 모킹하고 있으므로 Repository 메서드명 변경에 영향을 받지 않는다. 별도 수정 불필요.

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.controller.RoomControllerTest" --info 2>&1 | tail -20`
Expected: 모든 테스트 PASSED

- [ ] **Step 2: 전체 테스트 실행**

Run: `./gradlew test --info 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL, 모든 테스트 PASSED

- [ ] **Step 3: 커밋 (필요 시)**

전체 테스트 통과 확인 후, 추가 수정이 필요했다면 해당 내용 커밋.

---

### Task 9: ERD 문서 갱신

**Files:**
- Modify: `docs/ai/erd.md:42,216,226`

- [ ] **Step 1: rooms 테이블에서 `deleted_at` 컬럼 제거**

`docs/ai/erd.md`의 rooms 테이블 정의(42행)에서 다음 행을 제거한다:

```markdown
| deleted_at | TIMESTAMP | NULLABLE | Soft delete 시각 |
```

- [ ] **Step 2: 설계 포인트 8번 갱신**

`docs/ai/erd.md`의 설계 포인트 8번(216행)을 다음으로 교체한다:

```markdown
8. **방 Hard Delete:** 방 삭제 시 서비스 레이어에서 RoomMember를 먼저 물리 삭제한 후 Room을 물리 삭제한다. JPA Cascade 대신 명시적 서비스 레이어 삭제로 단방향 관계를 유지한다.
```

- [ ] **Step 3: TODO에서 완료 항목 갱신**

`docs/ai/erd.md`의 TODO 섹션(226행)에서 다음 항목을 제거하거나 완료 표시한다:

```markdown
// 변경 전
- [ ] 방 삭제 정책: soft delete vs hard delete + CASCADE
// 변경 후
- [x] 방 삭제 정책: hard delete 전환 완료 (서비스 레이어 명시적 삭제)
```

- [ ] **Step 4: 커밋**

```bash
git add docs/ai/erd.md
git commit -m "docs: ERD 문서에서 rooms soft delete를 hard delete로 갱신"
```
