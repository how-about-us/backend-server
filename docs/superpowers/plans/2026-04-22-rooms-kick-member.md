# Rooms 멤버 추방 구현 Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HOST가 방 멤버를 추방하는 `DELETE /rooms/{roomId}/members/{memberId}` API를 구현한다.

**Architecture:** `RoomInviteService`에 추방 메서드를 추가하고 (멤버 관리 책임이 같은 서비스), 기존 `RoomController`에 엔드포인트를 추가한다.

**Tech Stack:** Spring Boot 4.0, Spring Data JPA, Java 21, Lombok, JUnit 5 + Mockito

**참조:** `docs/superpowers/specs/2026-04-19-rooms-design.md` 5-2-1절, 6절

**선행 조건:** Plan A (초대 & 입장) 구현 완료 — `RoomInviteService`가 존재해야 한다.

---

## File Structure

| Action | Path | 역할 |
|--------|------|------|
| Modify | `common/error/ErrorCode.java` | `CANNOT_KICK_HOST`, `MEMBER_NOT_FOUND` 추가 |
| Modify | `rooms/service/RoomInviteService.java` | `kickMember` 메서드 추가 |
| Modify | `rooms/controller/RoomController.java` | DELETE 엔드포인트 추가 |
| Modify | `test/.../rooms/service/RoomInviteServiceTest.java` | 추방 테스트 추가 |
| Modify | `test/.../rooms/controller/RoomControllerTest.java` | 컨트롤러 테스트 추가 |

> 경로 프리픽스: `src/main/java/com/howaboutus/backend/`
> 테스트 프리픽스: `src/test/java/com/howaboutus/backend/`

---

### Task 1: ErrorCode 추가

**Files:**
- Modify: `common/error/ErrorCode.java`

- [ ] **Step 1: ErrorCode 2개 추가**

`// 400 BAD REQUEST` 섹션에 추가:

```java
CANNOT_KICK_HOST(HttpStatus.BAD_REQUEST, "호스트는 추방할 수 없습니다"),
```

`// 404 NOT FOUND` 섹션에 추가:

```java
MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 멤버입니다"),
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/error/ErrorCode.java
git commit -m "feat: 멤버 추방 ErrorCode(CANNOT_KICK_HOST, MEMBER_NOT_FOUND) 추가"
```

---

### Task 2: kickMember 서비스 메서드 + 테스트

**Files:**
- Modify: `rooms/service/RoomInviteService.java`
- Modify: `test/.../rooms/service/RoomInviteServiceTest.java`

- [ ] **Step 1: RoomInviteService에 kickMember 메서드 추가**

```java
@Transactional
public void kickMember(UUID roomId, Long memberId, Long userId) {
    getActiveRoom(roomId);
    getHostMember(roomId, userId);

    RoomMember target = roomMemberRepository.findByIdAndRoom_Id(memberId, roomId)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    if (target.getRole() == RoomRole.HOST) {
        throw new CustomException(ErrorCode.CANNOT_KICK_HOST);
    }

    roomMemberRepository.delete(target);
}
```

- [ ] **Step 2: 테스트 — 정상 추방**

`RoomInviteServiceTest.java`에 추가:

```java
@Test
@DisplayName("HOST가 MEMBER를 추방하면 레코드가 삭제된다")
void kickMemberDeletesMember() {
    ReflectionTestUtils.setField(regularMember, "id", 10L);

    given(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, HOST_ID))
            .willReturn(Optional.of(hostMember));
    given(roomMemberRepository.findByIdAndRoom_Id(10L, ROOM_ID)).willReturn(Optional.of(regularMember));

    roomInviteService.kickMember(ROOM_ID, 10L, HOST_ID);

    verify(roomMemberRepository).delete(regularMember);
}
```

- [ ] **Step 3: 테스트 — HOST 추방 시도**

```java
@Test
@DisplayName("HOST를 추방하려고 하면 CANNOT_KICK_HOST 예외")
void kickMemberThrowsWhenTargetIsHost() {
    ReflectionTestUtils.setField(hostMember, "id", 1L);

    given(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, HOST_ID))
            .willReturn(Optional.of(hostMember));
    given(roomMemberRepository.findByIdAndRoom_Id(1L, ROOM_ID)).willReturn(Optional.of(hostMember));

    assertThatThrownBy(() -> roomInviteService.kickMember(ROOM_ID, 1L, HOST_ID))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CANNOT_KICK_HOST);
}
```

- [ ] **Step 4: 테스트 — 존재하지 않는 멤버**

```java
@Test
@DisplayName("존재하지 않는 멤버를 추방하면 MEMBER_NOT_FOUND 예외")
void kickMemberThrowsWhenMemberNotFound() {
    given(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, HOST_ID))
            .willReturn(Optional.of(hostMember));
    given(roomMemberRepository.findByIdAndRoom_Id(999L, ROOM_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> roomInviteService.kickMember(ROOM_ID, 999L, HOST_ID))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
}
```

- [ ] **Step 5: 테스트 — MEMBER가 추방 시도**

```java
@Test
@DisplayName("MEMBER가 추방을 시도하면 NOT_ROOM_HOST 예외")
void kickMemberThrowsWhenNotHost() {
    given(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, MEMBER_ID))
            .willReturn(Optional.of(regularMember));

    assertThatThrownBy(() -> roomInviteService.kickMember(ROOM_ID, 10L, MEMBER_ID))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_ROOM_HOST);
}
```

- [ ] **Step 6: 테스트 실행**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomInviteServiceTest"`
Expected: ALL PASSED (기존 13 + 신규 4 = 17 tests)

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/service/RoomInviteService.java \
       src/test/java/com/howaboutus/backend/rooms/service/RoomInviteServiceTest.java
git commit -m "feat: 멤버 추방(kickMember) 구현 및 테스트"
```

---

### Task 3: 컨트롤러 엔드포인트 + 테스트

**Files:**
- Modify: `rooms/controller/RoomController.java`
- Modify: `test/.../rooms/controller/RoomControllerTest.java`

- [ ] **Step 1: RoomController에 DELETE 엔드포인트 추가**

```java
@Operation(summary = "멤버 추방", description = "방 멤버를 추방합니다. HOST만 가능합니다.")
@DeleteMapping("/{roomId}/members/{memberId}")
public ResponseEntity<Void> kickMember(
        @RequestHeader("X-User-Id") Long userId,
        @PathVariable UUID roomId,
        @PathVariable Long memberId
) {
    roomInviteService.kickMember(roomId, memberId, userId);
    return ResponseEntity.noContent().build();
}
```

- [ ] **Step 2: 컨트롤러 테스트 — 정상 추방**

`RoomControllerTest.java`에 추가:

```java
@Test
@DisplayName("멤버 추방 성공 시 204를 반환한다")
void kickMemberReturns204() throws Exception {
    mockMvc.perform(delete("/rooms/{roomId}/members/{memberId}", ROOM_ID, 10)
                    .header("X-User-Id", USER_ID))
            .andExpect(status().isNoContent());

    then(roomInviteService).should().kickMember(ROOM_ID, 10L, USER_ID);
}
```

- [ ] **Step 3: 컨트롤러 테스트 — HOST 추방 시도 시 400**

```java
@Test
@DisplayName("HOST를 추방하려고 하면 400을 반환한다")
void kickMemberReturns400WhenTargetIsHost() throws Exception {
    willThrow(new CustomException(ErrorCode.CANNOT_KICK_HOST))
            .given(roomInviteService).kickMember(ROOM_ID, 1L, USER_ID);

    mockMvc.perform(delete("/rooms/{roomId}/members/{memberId}", ROOM_ID, 1)
                    .header("X-User-Id", USER_ID))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CANNOT_KICK_HOST"));
}
```

- [ ] **Step 4: 전체 테스트 실행**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.*"`
Expected: ALL PASSED

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/controller/RoomController.java \
       src/test/java/com/howaboutus/backend/rooms/controller/RoomControllerTest.java
git commit -m "feat: 멤버 추방 API 엔드포인트 및 컨트롤러 테스트 추가"
```

---

### Task 4: 전체 빌드 검증

**Files:** 없음 (검증만)

- [ ] **Step 1: 전체 빌드**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 전체 테스트 확인**

Run: `./gradlew test`
Expected: ALL PASSED
