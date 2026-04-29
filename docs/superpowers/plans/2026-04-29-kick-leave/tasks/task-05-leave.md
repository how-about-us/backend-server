### Task 5: RoomMemberService — leave() 구현 (TDD)

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/service/RoomMemberService.java`
- Modify: `src/test/java/com/howaboutus/backend/rooms/service/RoomMemberServiceTest.java`

**선행 조건:** Task 4 (kick) 완료 필요 (같은 파일 수정)

- [ ] **Step 1: leave 성공 테스트 작성**

import 추가:

```java
import com.howaboutus.backend.realtime.event.MemberLeftEvent;
```

테스트 메서드:

```java
@Test
@DisplayName("leave 성공 - 멤버 삭제 + 이벤트 발행")
void leaveDeletesMemberAndPublishesEvent() {
    User member = User.ofGoogle("g2", "member@test.com", "멤버", "https://img/member.jpg");
    ReflectionTestUtils.setField(member, "id", TARGET_USER_ID);

    Room room = Room.create("여행", "부산", null, null, "invite1", HOST_USER_ID);
    ReflectionTestUtils.setField(room, "id", ROOM_ID);

    RoomMember regularMember = RoomMember.of(room, member, RoomRole.MEMBER);

    given(roomAuthorizationService.requireActiveMember(ROOM_ID, TARGET_USER_ID))
            .willReturn(regularMember);

    roomMemberService.leave(ROOM_ID, TARGET_USER_ID);

    then(roomMemberRepository).should().delete(regularMember);
    then(eventPublisher).should().publishEvent(any(MemberLeftEvent.class));
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomMemberServiceTest.leaveDeletesMemberAndPublishesEvent"`
Expected: FAIL — `leave` 메서드가 없음

- [ ] **Step 3: leave 메서드 구현**

`RoomMemberService.java`에 추가:

```java
import com.howaboutus.backend.realtime.event.MemberLeftEvent;
```

```java
@Transactional
public void leave(UUID roomId, Long userId) {
    RoomMember member = roomAuthorizationService.requireActiveMember(roomId, userId);

    if (member.getRole() == RoomRole.HOST) {
        throw new CustomException(ErrorCode.CANNOT_LEAVE_AS_HOST);
    }

    roomMemberRepository.delete(member);
    eventPublisher.publishEvent(new MemberLeftEvent(
            roomId,
            member.getUser().getId(),
            member.getUser().getNickname(),
            member.getUser().getProfileImageUrl()
    ));
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomMemberServiceTest.leaveDeletesMemberAndPublishesEvent"`
Expected: PASS

- [ ] **Step 5: leave 예외 테스트 2개 작성**

```java
@Test
@DisplayName("leave - HOST가 나가려 하면 CANNOT_LEAVE_AS_HOST")
void leaveThrowsWhenUserIsHost() {
    User host = User.ofGoogle("g1", "host@test.com", "호스트", null);
    ReflectionTestUtils.setField(host, "id", HOST_USER_ID);

    Room room = Room.create("여행", "부산", null, null, "invite1", HOST_USER_ID);
    ReflectionTestUtils.setField(room, "id", ROOM_ID);

    RoomMember hostMember = RoomMember.of(room, host, RoomRole.HOST);

    given(roomAuthorizationService.requireActiveMember(ROOM_ID, HOST_USER_ID))
            .willReturn(hostMember);

    assertThatThrownBy(() -> roomMemberService.leave(ROOM_ID, HOST_USER_ID))
            .isInstanceOf(CustomException.class)
            .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.CANNOT_LEAVE_AS_HOST));
}

@Test
@DisplayName("leave - 비멤버가 나가려 하면 NOT_ROOM_MEMBER")
void leaveThrowsWhenNotMember() {
    given(roomAuthorizationService.requireActiveMember(ROOM_ID, TARGET_USER_ID))
            .willThrow(new CustomException(ErrorCode.NOT_ROOM_MEMBER));

    assertThatThrownBy(() -> roomMemberService.leave(ROOM_ID, TARGET_USER_ID))
            .isInstanceOf(CustomException.class)
            .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_ROOM_MEMBER));
}
```

- [ ] **Step 6: 테스트 실행 — 전체 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomMemberServiceTest"`
Expected: ALL PASS

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/service/RoomMemberService.java \
        src/test/java/com/howaboutus/backend/rooms/service/RoomMemberServiceTest.java
git commit -m "feat: RoomMemberService.leave() 구현 및 단위 테스트"
```
