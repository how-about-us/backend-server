### Task 4: RoomMemberService — kick() 구현 (TDD)

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/service/RoomMemberService.java`
- Modify: `src/test/java/com/howaboutus/backend/rooms/service/RoomMemberServiceTest.java`

**선행 조건:** Task 1 (ErrorCode), Task 2 (Event Records) 완료 필요

- [ ] **Step 1: kick 성공 테스트 작성**

`RoomMemberServiceTest.java`에 추가. 먼저 클래스 상단에 새 mock 필드를 추가:

```java
@Mock private ApplicationEventPublisher eventPublisher;
```

import 추가:

```java
import org.springframework.context.ApplicationEventPublisher;
import com.howaboutus.backend.realtime.event.MemberKickedEvent;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import java.util.Optional;
```

`setUp()` 메서드 수정 — `RoomMemberService` 생성자에 `eventPublisher` 추가:

```java
@BeforeEach
void setUp() {
    roomMemberService = new RoomMemberService(
            roomMemberRepository, roomPresenceService, roomAuthorizationService, eventPublisher);
}
```

상수 추가:

```java
private static final Long HOST_USER_ID = 1L;
private static final Long TARGET_USER_ID = 2L;
```

테스트 메서드:

```java
@Test
@DisplayName("kick 성공 - 멤버 삭제 + 이벤트 발행")
void kickDeletesMemberAndPublishesEvent() {
    User host = User.ofGoogle("g1", "host@test.com", "호스트", "https://img/host.jpg");
    ReflectionTestUtils.setField(host, "id", HOST_USER_ID);
    User target = User.ofGoogle("g2", "target@test.com", "타겟", "https://img/target.jpg");
    ReflectionTestUtils.setField(target, "id", TARGET_USER_ID);

    Room room = Room.create("여행", "부산", null, null, "invite1", HOST_USER_ID);
    ReflectionTestUtils.setField(room, "id", ROOM_ID);

    RoomMember hostMember = RoomMember.of(room, host, RoomRole.HOST);
    RoomMember targetMember = RoomMember.of(room, target, RoomRole.MEMBER);

    given(roomAuthorizationService.requireHost(ROOM_ID, HOST_USER_ID)).willReturn(hostMember);
    given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, TARGET_USER_ID))
            .willReturn(Optional.of(targetMember));

    roomMemberService.kick(ROOM_ID, TARGET_USER_ID, HOST_USER_ID);

    then(roomMemberRepository).should().delete(targetMember);
    then(eventPublisher).should().publishEvent(any(MemberKickedEvent.class));
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomMemberServiceTest.kickDeletesMemberAndPublishesEvent"`
Expected: FAIL — `kick` 메서드가 없음, 생성자 파라미터 불일치

- [ ] **Step 3: kick 메서드 구현**

`RoomMemberService.java` 수��:

의존성 추가:

```java
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.realtime.event.MemberKickedEvent;
import org.springframework.context.ApplicationEventPublisher;
```

필드 추가:

```java
private final ApplicationEventPublisher eventPublisher;
```

메서드 추가:

```java
@Transactional
public void kick(UUID roomId, Long targetUserId, Long hostUserId) {
    roomAuthorizationService.requireHost(roomId, hostUserId);

    RoomMember target = roomMemberRepository.findByRoom_IdAndUser_Id(roomId, targetUserId)
            .orElseThrow(() -> new CustomException(ErrorCode.JOIN_REQUEST_NOT_FOUND));

    if (target.getRole() == RoomRole.HOST) {
        throw new CustomException(ErrorCode.CANNOT_KICK_HOST);
    }
    if (target.getRole() != RoomRole.MEMBER) {
        throw new CustomException(ErrorCode.KICK_TARGET_NOT_MEMBER);
    }

    roomMemberRepository.delete(target);
    eventPublisher.publishEvent(new MemberKickedEvent(
            roomId,
            target.getUser().getId(),
            target.getUser().getNickname(),
            target.getUser().getProfileImageUrl()
    ));
}
```

> **주의:** `JOIN_REQUEST_NOT_FOUND`(404)를 재활용하여 "해당 userId의 멤버가 없음"을 표현한다.

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomMemberServiceTest.kickDeletesMemberAndPublishesEvent"`
Expected: PASS

- [ ] **Step 5: kick 예외 테스트 4개 작성**

```java
@Test
@DisplayName("kick - HOST를 추방하려 하면 CANNOT_KICK_HOST")
void kickThrowsWhenTargetIsHost() {
    User host = User.ofGoogle("g1", "host@test.com", "호스트", null);
    ReflectionTestUtils.setField(host, "id", HOST_USER_ID);
    User anotherHost = User.ofGoogle("g3", "host2@test.com", "호스트2", null);
    ReflectionTestUtils.setField(anotherHost, "id", TARGET_USER_ID);

    Room room = Room.create("여행", "부산", null, null, "invite1", HOST_USER_ID);
    ReflectionTestUtils.setField(room, "id", ROOM_ID);

    RoomMember hostMember = RoomMember.of(room, host, RoomRole.HOST);
    RoomMember targetHostMember = RoomMember.of(room, anotherHost, RoomRole.HOST);

    given(roomAuthorizationService.requireHost(ROOM_ID, HOST_USER_ID)).willReturn(hostMember);
    given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, TARGET_USER_ID))
            .willReturn(Optional.of(targetHostMember));

    assertThatThrownBy(() -> roomMemberService.kick(ROOM_ID, TARGET_USER_ID, HOST_USER_ID))
            .isInstanceOf(CustomException.class)
            .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.CANNOT_KICK_HOST));
}

@Test
@DisplayName("kick - PENDING 멤버 추방 시도 시 KICK_TARGET_NOT_MEMBER")
void kickThrowsWhenTargetIsPending() {
    User host = User.ofGoogle("g1", "host@test.com", "호스트", null);
    ReflectionTestUtils.setField(host, "id", HOST_USER_ID);
    User pending = User.ofGoogle("g4", "pending@test.com", "대기자", null);
    ReflectionTestUtils.setField(pending, "id", TARGET_USER_ID);

    Room room = Room.create("여행", "부산", null, null, "invite1", HOST_USER_ID);
    ReflectionTestUtils.setField(room, "id", ROOM_ID);

    RoomMember hostMember = RoomMember.of(room, host, RoomRole.HOST);
    RoomMember pendingMember = RoomMember.of(room, pending, RoomRole.PENDING);

    given(roomAuthorizationService.requireHost(ROOM_ID, HOST_USER_ID)).willReturn(hostMember);
    given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, TARGET_USER_ID))
            .willReturn(Optional.of(pendingMember));

    assertThatThrownBy(() -> roomMemberService.kick(ROOM_ID, TARGET_USER_ID, HOST_USER_ID))
            .isInstanceOf(CustomException.class)
            .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.KICK_TARGET_NOT_MEMBER));
}

@Test
@DisplayName("kick - 존재하지 않는 userId면 404")
void kickThrowsWhenTargetNotFound() {
    User host = User.ofGoogle("g1", "host@test.com", "호스트", null);
    ReflectionTestUtils.setField(host, "id", HOST_USER_ID);

    Room room = Room.create("여행", "부산", null, null, "invite1", HOST_USER_ID);
    ReflectionTestUtils.setField(room, "id", ROOM_ID);

    RoomMember hostMember = RoomMember.of(room, host, RoomRole.HOST);

    given(roomAuthorizationService.requireHost(ROOM_ID, HOST_USER_ID)).willReturn(hostMember);
    given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, TARGET_USER_ID))
            .willReturn(Optional.empty());

    assertThatThrownBy(() -> roomMemberService.kick(ROOM_ID, TARGET_USER_ID, HOST_USER_ID))
            .isInstanceOf(CustomException.class);
}

@Test
@DisplayName("kick - HOST가 아닌 사용자가 시도하면 NOT_ROOM_HOST")
void kickThrowsWhenCallerIsNotHost() {
    given(roomAuthorizationService.requireHost(ROOM_ID, TARGET_USER_ID))
            .willThrow(new CustomException(ErrorCode.NOT_ROOM_HOST));

    assertThatThrownBy(() -> roomMemberService.kick(ROOM_ID, HOST_USER_ID, TARGET_USER_ID))
            .isInstanceOf(CustomException.class)
            .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_ROOM_HOST));
}
```

- [ ] **Step 6: 테스트 실행 — 전체 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomMemberServiceTest"`
Expected: ALL PASS

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/service/RoomMemberService.java \
        src/test/java/com/howaboutus/backend/rooms/service/RoomMemberServiceTest.java
git commit -m "feat: RoomMemberService.kick() 구현 및 단위 테스트"
```
