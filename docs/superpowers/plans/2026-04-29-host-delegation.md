# Host Delegation (방장 위임) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HOST가 특정 MEMBER에게 방장 권한을 위임하는 PATCH `/rooms/{roomId}/host` 엔드포인트 구현

**Architecture:** RoomMember 엔티티에 role 변경 도메인 메서드 추가, RoomMemberService에서 한 트랜잭션 role swap 후 AFTER_COMMIT 이벤트 발행, 리스너가 시스템 메시지 저장 + STOMP 브로드캐스트

**Tech Stack:** Spring Boot 4, Java 21, PostgreSQL, MongoDB (채팅), Redis (presence), WebSocket + STOMP, JUnit 5 + Mockito

---

## File Structure

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `src/main/java/.../common/error/ErrorCode.java` | ErrorCode 2개 추가 |
| Modify | `src/main/java/.../rooms/entity/RoomMember.java` | `promoteToHost()`, `demoteToMember()` 도메인 메서드 |
| Create | `src/main/java/.../realtime/event/HostDelegatedEvent.java` | 이벤트 record |
| Modify | `src/main/java/.../rooms/service/RoomMemberService.java` | `delegateHost()` 메서드 |
| Create | `src/main/java/.../messages/listener/HostDelegatedMessageListener.java` | 이벤트 리스너 |
| Modify | `src/main/java/.../messages/service/MessageService.java` | 시스템 메시지 메서드 |
| Create | `src/main/java/.../rooms/controller/dto/DelegateHostRequest.java` | 요청 DTO |
| Modify | `src/main/java/.../rooms/controller/RoomController.java` | PATCH 엔드포인트 |
| Create | `src/test/java/.../rooms/entity/RoomMemberTest.java` | 도메인 메서드 단위 테스트 |
| Modify | `src/test/java/.../rooms/service/RoomMemberServiceTest.java` | delegateHost 테스트 |
| Create | `src/test/java/.../messages/listener/HostDelegatedMessageListenerTest.java` | 리스너 테스트 |
| Modify | `src/test/java/.../rooms/controller/RoomControllerTest.java` | 컨트롤러 테스트 |
| Modify | `docs/ai/features.md` | 방장 위임 완료 표기 |

> 이하 패키지 루트: `src/main/java/com/howaboutus/backend`, 테스트 루트: `src/test/java/com/howaboutus/backend`

---

### Task 1: ErrorCode 추가

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/common/error/ErrorCode.java`

- [ ] **Step 1: ErrorCode 두 개 추가**

`KICK_TARGET_NOT_MEMBER` 아래에 위임 관련 에러코드 2개를 추가한다.

```java
    KICK_TARGET_NOT_MEMBER(HttpStatus.BAD_REQUEST, "추방 대상이 멤버가 아닙니다"),
    DELEGATE_TARGET_NOT_MEMBER(HttpStatus.BAD_REQUEST, "위임 대상이 멤버가 아닙니다"),
    CANNOT_DELEGATE_TO_SELF(HttpStatus.BAD_REQUEST, "자기 자신에게 방장을 위임할 수 없습니다"),
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/howaboutus/backend/common/error/ErrorCode.java
git commit -m "feat: DELEGATE_TARGET_NOT_MEMBER, CANNOT_DELEGATE_TO_SELF ErrorCode 추가"
```

---

### Task 2: RoomMember 도메인 메서드 + 테스트

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/entity/RoomMember.java`
- Create: `src/test/java/com/howaboutus/backend/rooms/entity/RoomMemberTest.java`

- [ ] **Step 1: 테스트 파일 작성**

```java
package com.howaboutus.backend.rooms.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoomMemberTest {

    private RoomMember createMember(RoomRole role) {
        Room room = Room.create("여행", "부산", null, null, "invite1", 1L);
        return RoomMember.of(room,
                com.howaboutus.backend.user.entity.User.ofGoogle("g1", "test@test.com", "테스터", null),
                role);
    }

    @Test
    @DisplayName("promoteToHost - MEMBER를 HOST로 승격")
    void promoteToHostSuccess() {
        RoomMember member = createMember(RoomRole.MEMBER);

        member.promoteToHost();

        assertThat(member.getRole()).isEqualTo(RoomRole.HOST);
    }

    @Test
    @DisplayName("promoteToHost - MEMBER가 아니면 예외")
    void promoteToHostFailsWhenNotMember() {
        RoomMember host = createMember(RoomRole.HOST);

        assertThatThrownBy(host::promoteToHost)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("promoteToHost - PENDING이면 예외")
    void promoteToHostFailsWhenPending() {
        RoomMember pending = createMember(RoomRole.PENDING);

        assertThatThrownBy(pending::promoteToHost)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("demoteToMember - HOST를 MEMBER로 강등")
    void demoteToMemberSuccess() {
        RoomMember host = createMember(RoomRole.HOST);

        host.demoteToMember();

        assertThat(host.getRole()).isEqualTo(RoomRole.MEMBER);
    }

    @Test
    @DisplayName("demoteToMember - HOST가 아니면 예외")
    void demoteToMemberFailsWhenNotHost() {
        RoomMember member = createMember(RoomRole.MEMBER);

        assertThatThrownBy(member::demoteToMember)
                .isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.entity.RoomMemberTest" 2>&1 | tail -10`
Expected: FAIL — `promoteToHost()`, `demoteToMember()` 메서드가 없음

- [ ] **Step 3: 도메인 메서드 구현**

`RoomMember.java`의 `approve()` 메서드 아래에 추가:

```java
    public void promoteToHost() {
        if (this.role != RoomRole.MEMBER) {
            throw new IllegalStateException("MEMBER 상태의 멤버만 HOST로 승격할 수 있습니다. 현재 상태: " + this.role);
        }
        this.role = RoomRole.HOST;
    }

    public void demoteToMember() {
        if (this.role != RoomRole.HOST) {
            throw new IllegalStateException("HOST 상태의 멤버만 MEMBER로 강등할 수 있습니다. 현재 상태: " + this.role);
        }
        this.role = RoomRole.MEMBER;
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.entity.RoomMemberTest" 2>&1 | tail -10`
Expected: PASS — 5 tests passed

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/howaboutus/backend/rooms/entity/RoomMember.java \
       src/test/java/com/howaboutus/backend/rooms/entity/RoomMemberTest.java
git commit -m "feat: RoomMember.promoteToHost(), demoteToMember() 도메인 메서드 추가"
```

---

### Task 3: HostDelegatedEvent record

**Files:**
- Create: `src/main/java/com/howaboutus/backend/realtime/event/HostDelegatedEvent.java`

- [ ] **Step 1: 이벤트 record 생성**

```java
package com.howaboutus.backend.realtime.event;

import java.util.UUID;

public record HostDelegatedEvent(
        UUID roomId,
        long previousHostUserId,
        String previousHostNickname,
        long newHostUserId,
        String newHostNickname
) {}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/howaboutus/backend/realtime/event/HostDelegatedEvent.java
git commit -m "feat: HostDelegatedEvent record 추가"
```

---

### Task 4: RoomMemberService.delegateHost() + 테스트

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/service/RoomMemberService.java`
- Modify: `src/test/java/com/howaboutus/backend/rooms/service/RoomMemberServiceTest.java`

- [ ] **Step 1: delegateHost 테스트 추가**

`RoomMemberServiceTest.java`에 import 추가:

```java
import com.howaboutus.backend.realtime.event.HostDelegatedEvent;
```

테스트 메서드 추가 (클래스 끝, 마지막 테스트 아래):

```java
    @Test
    @DisplayName("delegateHost 성공 - role swap + 이벤트 발행")
    void delegateHostSwapsRolesAndPublishesEvent() {
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

        roomMemberService.delegateHost(ROOM_ID, TARGET_USER_ID, HOST_USER_ID);

        assertThat(hostMember.getRole()).isEqualTo(RoomRole.MEMBER);
        assertThat(targetMember.getRole()).isEqualTo(RoomRole.HOST);
        then(eventPublisher).should().publishEvent(any(HostDelegatedEvent.class));
    }

    @Test
    @DisplayName("delegateHost - 자기 자신에게 위임 시 CANNOT_DELEGATE_TO_SELF")
    void delegateHostThrowsWhenSelfDelegation() {
        User host = User.ofGoogle("g1", "host@test.com", "호스트", null);
        ReflectionTestUtils.setField(host, "id", HOST_USER_ID);

        Room room = Room.create("여행", "부산", null, null, "invite1", HOST_USER_ID);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);

        RoomMember hostMember = RoomMember.of(room, host, RoomRole.HOST);

        given(roomAuthorizationService.requireHost(ROOM_ID, HOST_USER_ID)).willReturn(hostMember);

        assertThatThrownBy(() -> roomMemberService.delegateHost(ROOM_ID, HOST_USER_ID, HOST_USER_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CANNOT_DELEGATE_TO_SELF));
    }

    @Test
    @DisplayName("delegateHost - 대상이 PENDING이면 DELEGATE_TARGET_NOT_MEMBER")
    void delegateHostThrowsWhenTargetIsPending() {
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

        assertThatThrownBy(() -> roomMemberService.delegateHost(ROOM_ID, TARGET_USER_ID, HOST_USER_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DELEGATE_TARGET_NOT_MEMBER));
    }

    @Test
    @DisplayName("delegateHost - 대상이 존재하지 않으면 JOIN_REQUEST_NOT_FOUND")
    void delegateHostThrowsWhenTargetNotFound() {
        User host = User.ofGoogle("g1", "host@test.com", "호스트", null);
        ReflectionTestUtils.setField(host, "id", HOST_USER_ID);

        Room room = Room.create("여행", "부산", null, null, "invite1", HOST_USER_ID);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);

        RoomMember hostMember = RoomMember.of(room, host, RoomRole.HOST);

        given(roomAuthorizationService.requireHost(ROOM_ID, HOST_USER_ID)).willReturn(hostMember);
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, TARGET_USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> roomMemberService.delegateHost(ROOM_ID, TARGET_USER_ID, HOST_USER_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.JOIN_REQUEST_NOT_FOUND));
    }

    @Test
    @DisplayName("delegateHost - HOST가 아닌 사용자가 시도하면 NOT_ROOM_HOST")
    void delegateHostThrowsWhenCallerIsNotHost() {
        given(roomAuthorizationService.requireHost(ROOM_ID, TARGET_USER_ID))
                .willThrow(new CustomException(ErrorCode.NOT_ROOM_HOST));

        assertThatThrownBy(() -> roomMemberService.delegateHost(ROOM_ID, HOST_USER_ID, TARGET_USER_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_ROOM_HOST));
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomMemberServiceTest" 2>&1 | tail -10`
Expected: FAIL — `delegateHost()` 메서드가 없음

- [ ] **Step 3: delegateHost() 구현**

`RoomMemberService.java`의 `leave()` 메서드 아래에 추가. import도 추가:

```java
import com.howaboutus.backend.realtime.event.HostDelegatedEvent;
```

```java
    @Transactional
    public void delegateHost(UUID roomId, Long targetUserId, Long hostUserId) {
        if (hostUserId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.CANNOT_DELEGATE_TO_SELF);
        }

        RoomMember hostMember = roomAuthorizationService.requireHost(roomId, hostUserId);

        RoomMember target = roomMemberRepository.findByRoom_IdAndUser_Id(roomId, targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.JOIN_REQUEST_NOT_FOUND));

        if (target.getRole() != RoomRole.MEMBER) {
            throw new CustomException(ErrorCode.DELEGATE_TARGET_NOT_MEMBER);
        }

        hostMember.demoteToMember();
        target.promoteToHost();

        eventPublisher.publishEvent(new HostDelegatedEvent(
                roomId,
                hostMember.getUser().getId(),
                hostMember.getUser().getNickname(),
                target.getUser().getId(),
                target.getUser().getNickname()
        ));
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomMemberServiceTest" 2>&1 | tail -10`
Expected: PASS — 모든 테스트 통과

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/howaboutus/backend/rooms/service/RoomMemberService.java \
       src/test/java/com/howaboutus/backend/rooms/service/RoomMemberServiceTest.java
git commit -m "feat: RoomMemberService.delegateHost() 방장 위임 서비스 메서드 추가"
```

---

### Task 5: MessageService.sendHostDelegatedSystemMessage()

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/messages/service/MessageService.java`

- [ ] **Step 1: 시스템 메시지 메서드 추가**

`MessageService.java`의 `sendMemberLeftSystemMessage()` 메서드 아래에 추가:

```java
    public MessageResult sendHostDelegatedSystemMessage(UUID roomId,
                                                        long previousHostUserId,
                                                        String previousHostNickname,
                                                        long newHostUserId,
                                                        String newHostNickname) {
        String normalizedPrevNickname = normalizeContent(previousHostNickname);
        String normalizedNewNickname = normalizeContent(newHostNickname);
        Map<String, Object> metadata = nonNullMetadata(metadataEntries(
                "eventType", "HOST_DELEGATED",
                "previousHostUserId", previousHostUserId,
                "previousHostNickname", normalizedPrevNickname,
                "newHostUserId", newHostUserId,
                "newHostNickname", normalizedNewNickname
        ));

        ChatMessage message = ChatMessage.system(
                roomId,
                normalizedPrevNickname + "님이 " + normalizedNewNickname + "님에게 방장을 위임했습니다",
                metadata);
        ChatMessage savedMessage = chatMessageRepository.save(message);
        MessageResult result = MessageResult.from(savedMessage);
        try {
            eventPublisher.publishEvent(MessageSentEvent.from(result));
        } catch (Exception e) {
            log.warn("브로드캐스트 실패, 메시지 저장은 완료: messageId={}", result.id(), e);
        }
        return result;
    }
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/howaboutus/backend/messages/service/MessageService.java
git commit -m "feat: MessageService.sendHostDelegatedSystemMessage() 추가"
```

---

### Task 6: HostDelegatedMessageListener + 테스트

**Files:**
- Create: `src/main/java/com/howaboutus/backend/messages/listener/HostDelegatedMessageListener.java`
- Create: `src/test/java/com/howaboutus/backend/messages/listener/HostDelegatedMessageListenerTest.java`

- [ ] **Step 1: 리스너 테스트 작성**

```java
package com.howaboutus.backend.messages.listener;

import static org.mockito.BDDMockito.then;

import com.howaboutus.backend.messages.service.MessageService;
import com.howaboutus.backend.realtime.event.HostDelegatedEvent;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HostDelegatedMessageListenerTest {

    private static final UUID ROOM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private MessageService messageService;

    private HostDelegatedMessageListener listener;

    @BeforeEach
    void setUp() {
        listener = new HostDelegatedMessageListener(messageService);
    }

    @Test
    @DisplayName("이벤트 처리 - 시스템 메시지 전송")
    void handleSendsSystemMessage() {
        HostDelegatedEvent event = new HostDelegatedEvent(
                ROOM_ID, 1L, "호스트", 2L, "타겟");

        listener.handle(event);

        then(messageService).should().sendHostDelegatedSystemMessage(
                ROOM_ID, 1L, "호스트", 2L, "타겟");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.messages.listener.HostDelegatedMessageListenerTest" 2>&1 | tail -10`
Expected: FAIL — `HostDelegatedMessageListener` 클래스가 없음

- [ ] **Step 3: 리스너 구현**

```java
package com.howaboutus.backend.messages.listener;

import com.howaboutus.backend.messages.service.MessageService;
import com.howaboutus.backend.realtime.event.HostDelegatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class HostDelegatedMessageListener {

    private final MessageService messageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(HostDelegatedEvent event) {
        messageService.sendHostDelegatedSystemMessage(
                event.roomId(),
                event.previousHostUserId(),
                event.previousHostNickname(),
                event.newHostUserId(),
                event.newHostNickname());
    }
}
```

> Note: kick/leave 리스너는 presence 제거를 하지만, 위임은 양쪽 멤버 모두 방에 남으므로 presence 작업이 필요 없다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.messages.listener.HostDelegatedMessageListenerTest" 2>&1 | tail -10`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/howaboutus/backend/messages/listener/HostDelegatedMessageListener.java \
       src/test/java/com/howaboutus/backend/messages/listener/HostDelegatedMessageListenerTest.java
git commit -m "feat: HostDelegatedMessageListener 이벤트 리스너 추가"
```

---

### Task 7: Controller 엔드포인트 + 테스트

**Files:**
- Create: `src/main/java/com/howaboutus/backend/rooms/controller/dto/DelegateHostRequest.java`
- Modify: `src/main/java/com/howaboutus/backend/rooms/controller/RoomController.java`
- Modify: `src/test/java/com/howaboutus/backend/rooms/controller/RoomControllerTest.java`

- [ ] **Step 1: 요청 DTO 생성**

```java
package com.howaboutus.backend.rooms.controller.dto;

import jakarta.validation.constraints.NotNull;

public record DelegateHostRequest(
        @NotNull(message = "위임 대상 userId는 필수입니다")
        Long targetUserId
) {}
```

- [ ] **Step 2: 컨트롤러 테스트 추가**

`RoomControllerTest.java`에 import 추가 (이미 있는 것은 생략):

```java
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
```

클래스 마지막 테스트 아래에 추가:

```java
    @Test
    @DisplayName("방장 위임 성공 시 200을 반환한다")
    void delegateHostReturns200() throws Exception {
        willDoNothing().given(roomMemberService).delegateHost(ROOM_ID, 2L, USER_ID);

        mockMvc.perform(patch("/rooms/{roomId}/host", ROOM_ID)
                        .cookie(new Cookie("access_token", VALID_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\": 2}"))
                .andExpect(status().isOk());

        then(roomMemberService).should().delegateHost(ROOM_ID, 2L, USER_ID);
    }

    @Test
    @DisplayName("HOST가 아닌 사용자가 위임 시도 시 403을 반환한다")
    void delegateHostReturns403ForNonHost() throws Exception {
        willThrow(new CustomException(ErrorCode.NOT_ROOM_HOST))
                .given(roomMemberService).delegateHost(ROOM_ID, 2L, USER_ID);

        mockMvc.perform(patch("/rooms/{roomId}/host", ROOM_ID)
                        .cookie(new Cookie("access_token", VALID_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\": 2}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("자기 자신에게 위임 시 400을 반환한다")
    void delegateHostReturns400ForSelfDelegation() throws Exception {
        willThrow(new CustomException(ErrorCode.CANNOT_DELEGATE_TO_SELF))
                .given(roomMemberService).delegateHost(ROOM_ID, 1L, USER_ID);

        mockMvc.perform(patch("/rooms/{roomId}/host", ROOM_ID)
                        .cookie(new Cookie("access_token", VALID_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\": 1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("targetUserId 누락 시 400을 반환한다")
    void delegateHostReturns400WhenTargetUserIdMissing() throws Exception {
        mockMvc.perform(patch("/rooms/{roomId}/host", ROOM_ID)
                        .cookie(new Cookie("access_token", VALID_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(roomMemberService);
    }
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.controller.RoomControllerTest.delegateHostReturns200" 2>&1 | tail -10`
Expected: FAIL — 엔드포인트가 없으므로 405 Method Not Allowed 또는 404

- [ ] **Step 4: 컨트롤러 엔드포인트 추가**

`RoomController.java`에 import 추가:

```java
import com.howaboutus.backend.rooms.controller.dto.DelegateHostRequest;
```

`kickMember()` 메서드 아래에 추가:

```java
    @Operation(summary = "방장 위임", description = "방장 권한을 다른 멤버에게 위임합니다. HOST만 가능합니다.")
    @PatchMapping("/{roomId}/host")
    public ResponseEntity<Void> delegateHost(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID roomId,
            @RequestBody @Valid DelegateHostRequest request
    ) {
        roomMemberService.delegateHost(roomId, request.targetUserId(), userId);
        return ResponseEntity.ok().build();
    }
```

- [ ] **Step 5: 전체 컨트롤러 테스트 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.controller.RoomControllerTest" 2>&1 | tail -10`
Expected: PASS — 모든 테스트 통과

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/howaboutus/backend/rooms/controller/dto/DelegateHostRequest.java \
       src/main/java/com/howaboutus/backend/rooms/controller/RoomController.java \
       src/test/java/com/howaboutus/backend/rooms/controller/RoomControllerTest.java
git commit -m "feat: PATCH /rooms/{roomId}/host 방장 위임 엔드포인트 추가"
```

---

### Task 8: 전체 테스트 실행 + features.md 갱신

**Files:**
- Modify: `docs/ai/features.md`

- [ ] **Step 1: 전체 테스트 실행**

Run: `./gradlew test 2>&1 | tail -15`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: features.md 방장 위임 완료 표기**

`docs/ai/features.md` 62행:

변경 전:
```markdown
| `[-]` | 방장 위임 | 권한 이전 | room_members |
```

변경 후:
```markdown
| `[x]` | 방장 위임 | HOST가 특정 MEMBER에게 방장 권한 위임 (PATCH /rooms/{roomId}/host) | room_members |
```

- [ ] **Step 3: features.md 미결 사항 업데이트**

`docs/ai/features.md` 158행:

변경 전:
```markdown
| 5 | 방장 위임 기능 | MVP 이후 진행 | 보류 |
```

변경 후:
```markdown
| 5 | 방장 위임 기능 | 구현 완료 (PATCH /rooms/{roomId}/host) | 확정 |
```

- [ ] **Step 4: Commit**

```bash
git add docs/ai/features.md
git commit -m "docs: features.md 방장 위임 구현 완료 표기"
```
