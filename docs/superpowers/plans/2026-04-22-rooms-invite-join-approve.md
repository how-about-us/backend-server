# Rooms 초대 & 입장 Part 2 — 입장 요청 + 상태 조회 + 승인/거절

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 입장 요청, 입장 상태 조회, 대기 요청 목록 조회, 승인, 거절 — 5개 서비스 메서드를 구현한다.

**Architecture:** Part 1에서 생성한 `RoomInviteService`에 메서드를 추가한다.

**Tech Stack:** Spring Boot 4.0, Spring Data JPA, Hibernate 7, Java 21, Lombok, JUnit 5 + Mockito

**참조:** `docs/superpowers/specs/2026-04-19-rooms-design.md` 5-2절, 6절

**시리즈:**
- rooms-invite-infra: 인프라 + DTO + 초대 코드 재발급
- **rooms-invite-join-approve (현재):** 입장 요청 + 상태 조회 + 승인/거절 서비스
- rooms-invite-controller: 컨트롤러 DTO + 엔드포인트 + 컨트롤러 테스트

**선행 조건:** rooms-invite-infra 완료 — `RoomInviteService`, 서비스 DTO, ErrorCode, Repository 메서드가 존재해야 한다.

---

## File Structure

| Action | Path | 역할 |
|--------|------|------|
| Modify | `rooms/service/RoomInviteService.java` | 5개 메서드 추가 |
| Modify | `test/.../rooms/service/RoomInviteServiceTest.java` | 11개 테스트 추가 |

> 경로 프리픽스: `src/main/java/com/howaboutus/backend/`
> 테스트 프리픽스: `src/test/java/com/howaboutus/backend/`

---

### Task 1: 입장 요청 (requestJoin) 서비스 + 테스트

**Files:**
- Modify: `rooms/service/RoomInviteService.java`
- Modify: `test/.../rooms/service/RoomInviteServiceTest.java`

- [ ] **Step 1: RoomInviteService에 UserRepository 의존성 추가**

생성자에 `UserRepository` 추가:

```java
import com.howaboutus.backend.user.entity.User;
import com.howaboutus.backend.user.repository.UserRepository;
```

필드 추가:

```java
private final UserRepository userRepository;
```

생성자 파라미터 순서: `roomRepository, roomMemberRepository, userRepository, inviteCodeGenerator`

- [ ] **Step 2: requestJoin 메서드 추가**

`RoomInviteService.java`에 추가:

```java
import com.howaboutus.backend.rooms.service.dto.JoinResult;
import java.util.Optional;

@Transactional
public JoinResult requestJoin(String inviteCode, Long userId) {
    Room room = roomRepository.findByInviteCodeAndDeletedAtIsNull(inviteCode)
            .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

    Optional<RoomMember> existing = roomMemberRepository.findByRoom_IdAndUser_Id(room.getId(), userId);

    if (existing.isPresent()) {
        RoomMember member = existing.get();
        if (member.getRole() == RoomRole.PENDING) {
            return JoinResult.pending(room.getTitle());
        }
        return JoinResult.alreadyMember(room.getId(), room.getTitle(), member.getRole());
    }

    User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    roomMemberRepository.save(RoomMember.of(room, user, RoomRole.PENDING));
    return JoinResult.pending(room.getTitle());
}
```

- [ ] **Step 3: RoomInviteServiceTest setUp 업데이트 + 테스트 추가**

`setUp()`의 생성자 호출을 업데이트:

```java
roomInviteService = new RoomInviteService(
        roomRepository, roomMemberRepository, userRepository, inviteCodeGenerator);
```

테스트 메서드 추가:

```java
import com.howaboutus.backend.rooms.service.dto.JoinResult;

@Test
@DisplayName("초대 코드로 입장 요청 시 PENDING 멤버로 등록한다")
void requestJoinCreatesPendingMember() {
    given(roomRepository.findByInviteCodeAndDeletedAtIsNull("aB3xK9mQ2w"))
            .willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, STRANGER_ID))
            .willReturn(Optional.empty());
    User stranger = User.ofGoogle("google-stranger", "stranger@test.com", "낯선이", null);
    ReflectionTestUtils.setField(stranger, "id", STRANGER_ID);
    given(userRepository.findById(STRANGER_ID)).willReturn(Optional.of(stranger));

    JoinResult result = roomInviteService.requestJoin("aB3xK9mQ2w", STRANGER_ID);

    assertThat(result.status()).isEqualTo("pending");
    assertThat(result.roomTitle()).isEqualTo("부산 여행");
}

@Test
@DisplayName("이미 MEMBER인 사용자가 입장 요청하면 already_member를 반환한다")
void requestJoinReturnsAlreadyMemberWhenMember() {
    given(roomRepository.findByInviteCodeAndDeletedAtIsNull("aB3xK9mQ2w"))
            .willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, MEMBER_ID))
            .willReturn(Optional.of(regularMember));

    JoinResult result = roomInviteService.requestJoin("aB3xK9mQ2w", MEMBER_ID);

    assertThat(result.status()).isEqualTo("already_member");
    assertThat(result.roomId()).isEqualTo(ROOM_ID);
    assertThat(result.role()).isEqualTo(RoomRole.MEMBER);
}

@Test
@DisplayName("이미 PENDING인 사용자가 입장 요청하면 pending을 반환한다")
void requestJoinReturnsPendingWhenAlreadyPending() {
    User pendingUser = User.ofGoogle("google-pending", "pending@test.com", "대기자", null);
    ReflectionTestUtils.setField(pendingUser, "id", 3L);
    RoomMember pendingMember = RoomMember.of(room, pendingUser, RoomRole.PENDING);

    given(roomRepository.findByInviteCodeAndDeletedAtIsNull("aB3xK9mQ2w"))
            .willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, 3L))
            .willReturn(Optional.of(pendingMember));

    JoinResult result = roomInviteService.requestJoin("aB3xK9mQ2w", 3L);

    assertThat(result.status()).isEqualTo("pending");
    assertThat(result.roomTitle()).isEqualTo("부산 여행");
}

@Test
@DisplayName("존재하지 않는 초대 코드로 입장 요청하면 ROOM_NOT_FOUND 예외")
void requestJoinThrowsWhenInvalidCode() {
    given(roomRepository.findByInviteCodeAndDeletedAtIsNull("invalidCode"))
            .willReturn(Optional.empty());

    assertThatThrownBy(() -> roomInviteService.requestJoin("invalidCode", STRANGER_ID))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
}
```

- [ ] **Step 4: 테스트 실행**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomInviteServiceTest"`
Expected: 6 tests PASSED

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/service/RoomInviteService.java \
       src/test/java/com/howaboutus/backend/rooms/service/RoomInviteServiceTest.java
git commit -m "feat: 초대 코드로 입장 요청 기능 구현 및 테스트"
```

---

### Task 2: 입장 상태 조회 + 대기 목록 + 승인 + 거절 서비스 + 테스트

**Files:**
- Modify: `rooms/service/RoomInviteService.java`
- Modify: `test/.../rooms/service/RoomInviteServiceTest.java`

- [ ] **Step 1: getJoinStatus 메서드 추가**

`RoomInviteService.java`에 추가:

```java
import com.howaboutus.backend.rooms.service.dto.JoinStatusResult;

public JoinStatusResult getJoinStatus(String inviteCode, Long userId) {
    Room room = roomRepository.findByInviteCodeAndDeletedAtIsNull(inviteCode)
            .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

    RoomMember member = roomMemberRepository.findByRoom_IdAndUser_Id(room.getId(), userId)
            .orElseThrow(() -> new CustomException(ErrorCode.JOIN_REQUEST_NOT_FOUND));

    if (member.getRole() == RoomRole.PENDING) {
        return JoinStatusResult.pending(room.getTitle());
    }
    return JoinStatusResult.approved(room.getId(), room.getTitle(), member.getRole());
}
```

- [ ] **Step 2: getJoinRequests 메서드 추가**

```java
import com.howaboutus.backend.rooms.service.dto.JoinRequestResult;
import java.util.List;

public List<JoinRequestResult> getJoinRequests(UUID roomId, Long userId) {
    Room room = getActiveRoom(roomId);
    getHostMember(roomId, userId);

    List<RoomMember> pendingMembers = roomMemberRepository.findByRoom_IdAndRole(roomId, RoomRole.PENDING);

    return pendingMembers.stream()
            .map(m -> new JoinRequestResult(
                    m.getId(),
                    m.getUser().getId(),
                    m.getUser().getNickname(),
                    m.getUser().getProfileImageUrl(),
                    m.getJoinedAt()))
            .toList();
}
```

- [ ] **Step 3: approve 메서드 추가**

```java
@Transactional
public void approve(UUID roomId, Long requestId, Long userId) {
    getActiveRoom(roomId);
    getHostMember(roomId, userId);

    RoomMember target = roomMemberRepository.findByIdAndRoom_Id(requestId, roomId)
            .orElseThrow(() -> new CustomException(ErrorCode.JOIN_REQUEST_NOT_FOUND));

    target.approve();
}
```

- [ ] **Step 4: reject 메서드 추가**

```java
@Transactional
public void reject(UUID roomId, Long requestId, Long userId) {
    getActiveRoom(roomId);
    getHostMember(roomId, userId);

    RoomMember target = roomMemberRepository.findByIdAndRoom_Id(requestId, roomId)
            .orElseThrow(() -> new CustomException(ErrorCode.JOIN_REQUEST_NOT_FOUND));

    roomMemberRepository.delete(target);
}
```

- [ ] **Step 5: 테스트 추가 — getJoinStatus (3개)**

`RoomInviteServiceTest.java`에 추가:

```java
import com.howaboutus.backend.rooms.service.dto.JoinStatusResult;

@Test
@DisplayName("PENDING 상태 사용자가 상태 조회하면 pending을 반환한다")
void getJoinStatusReturnsPending() {
    User pendingUser = User.ofGoogle("google-pending", "pending@test.com", "대기자", null);
    ReflectionTestUtils.setField(pendingUser, "id", 3L);
    RoomMember pendingMember = RoomMember.of(room, pendingUser, RoomRole.PENDING);

    given(roomRepository.findByInviteCodeAndDeletedAtIsNull("aB3xK9mQ2w"))
            .willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, 3L))
            .willReturn(Optional.of(pendingMember));

    JoinStatusResult result = roomInviteService.getJoinStatus("aB3xK9mQ2w", 3L);

    assertThat(result.status()).isEqualTo("pending");
}

@Test
@DisplayName("승인된 사용자가 상태 조회하면 approved를 반환한다")
void getJoinStatusReturnsApproved() {
    given(roomRepository.findByInviteCodeAndDeletedAtIsNull("aB3xK9mQ2w"))
            .willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, MEMBER_ID))
            .willReturn(Optional.of(regularMember));

    JoinStatusResult result = roomInviteService.getJoinStatus("aB3xK9mQ2w", MEMBER_ID);

    assertThat(result.status()).isEqualTo("approved");
    assertThat(result.roomId()).isEqualTo(ROOM_ID);
}

@Test
@DisplayName("거절된(레코드 없는) 사용자가 상태 조회하면 JOIN_REQUEST_NOT_FOUND 예외")
void getJoinStatusThrowsWhenRejected() {
    given(roomRepository.findByInviteCodeAndDeletedAtIsNull("aB3xK9mQ2w"))
            .willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, STRANGER_ID))
            .willReturn(Optional.empty());

    assertThatThrownBy(() -> roomInviteService.getJoinStatus("aB3xK9mQ2w", STRANGER_ID))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.JOIN_REQUEST_NOT_FOUND);
}
```

- [ ] **Step 6: 테스트 추가 — approve / reject / 권한 (4개)**

```java
import static org.mockito.Mockito.verify;
import java.util.List;
import com.howaboutus.backend.rooms.service.dto.JoinRequestResult;

@Test
@DisplayName("HOST가 입장 요청을 승인하면 PENDING → MEMBER로 변경된다")
void approveChangesRoleToMember() {
    User pendingUser = User.ofGoogle("google-pending", "pending@test.com", "대기자", null);
    ReflectionTestUtils.setField(pendingUser, "id", 3L);
    RoomMember pendingMember = RoomMember.of(room, pendingUser, RoomRole.PENDING);
    ReflectionTestUtils.setField(pendingMember, "id", 42L);

    given(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, HOST_ID))
            .willReturn(Optional.of(hostMember));
    given(roomMemberRepository.findByIdAndRoom_Id(42L, ROOM_ID))
            .willReturn(Optional.of(pendingMember));

    roomInviteService.approve(ROOM_ID, 42L, HOST_ID);

    assertThat(pendingMember.getRole()).isEqualTo(RoomRole.MEMBER);
}

@Test
@DisplayName("HOST가 입장 요청을 거절하면 레코드가 삭제된다")
void rejectDeletesMember() {
    User pendingUser = User.ofGoogle("google-pending", "pending@test.com", "대기자", null);
    ReflectionTestUtils.setField(pendingUser, "id", 3L);
    RoomMember pendingMember = RoomMember.of(room, pendingUser, RoomRole.PENDING);
    ReflectionTestUtils.setField(pendingMember, "id", 42L);

    given(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, HOST_ID))
            .willReturn(Optional.of(hostMember));
    given(roomMemberRepository.findByIdAndRoom_Id(42L, ROOM_ID))
            .willReturn(Optional.of(pendingMember));

    roomInviteService.reject(ROOM_ID, 42L, HOST_ID);

    verify(roomMemberRepository).delete(pendingMember);
}

@Test
@DisplayName("존재하지 않는 요청을 승인하면 JOIN_REQUEST_NOT_FOUND 예외")
void approveThrowsWhenRequestNotFound() {
    given(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, HOST_ID))
            .willReturn(Optional.of(hostMember));
    given(roomMemberRepository.findByIdAndRoom_Id(999L, ROOM_ID))
            .willReturn(Optional.empty());

    assertThatThrownBy(() -> roomInviteService.approve(ROOM_ID, 999L, HOST_ID))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.JOIN_REQUEST_NOT_FOUND);
}

@Test
@DisplayName("MEMBER가 승인을 시도하면 NOT_ROOM_HOST 예외")
void approveThrowsWhenNotHost() {
    given(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, MEMBER_ID))
            .willReturn(Optional.of(regularMember));

    assertThatThrownBy(() -> roomInviteService.approve(ROOM_ID, 42L, MEMBER_ID))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_ROOM_HOST);
}
```

- [ ] **Step 7: 테스트 추가 — getJoinRequests (1개)**

```java
@Test
@DisplayName("HOST가 대기 요청 목록을 조회하면 PENDING 멤버 목록을 반환한다")
void getJoinRequestsReturnsPendingMembers() {
    User pendingUser = User.ofGoogle("google-pending", "pending@test.com", "대기자", "http://img.png");
    ReflectionTestUtils.setField(pendingUser, "id", 3L);
    RoomMember pendingMember = RoomMember.of(room, pendingUser, RoomRole.PENDING);
    ReflectionTestUtils.setField(pendingMember, "id", 42L);

    given(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).willReturn(Optional.of(room));
    given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, HOST_ID))
            .willReturn(Optional.of(hostMember));
    given(roomMemberRepository.findByRoom_IdAndRole(ROOM_ID, RoomRole.PENDING))
            .willReturn(List.of(pendingMember));

    List<JoinRequestResult> results = roomInviteService.getJoinRequests(ROOM_ID, HOST_ID);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).requestId()).isEqualTo(42L);
    assertThat(results.get(0).nickname()).isEqualTo("대기자");
}
```

- [ ] **Step 8: 테스트 실행**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomInviteServiceTest"`
Expected: 13 tests PASSED

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/service/RoomInviteService.java \
       src/test/java/com/howaboutus/backend/rooms/service/RoomInviteServiceTest.java
git commit -m "feat: 입장 요청/상태 조회/승인/거절/대기 목록 구현 및 테스트"
```
