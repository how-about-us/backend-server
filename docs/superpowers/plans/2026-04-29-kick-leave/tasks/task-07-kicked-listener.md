### Task 7: MemberKickedMessageListener 생성 (TDD)

**Files:**
- Create: `src/main/java/com/howaboutus/backend/messages/listener/MemberKickedMessageListener.java`
- Create: `src/test/java/com/howaboutus/backend/messages/listener/MemberKickedMessageListenerTest.java`

**선행 조건:** Task 3 (removeAllSessions), Task 6 (MessageService 메서드) 완료 필요

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.howaboutus.backend.messages.listener;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

import com.howaboutus.backend.messages.service.MessageService;
import com.howaboutus.backend.realtime.event.MemberKickedEvent;
import com.howaboutus.backend.realtime.service.RoomPresenceService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberKickedMessageListenerTest {

    private static final UUID ROOM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private MessageService messageService;
    @Mock private RoomPresenceService roomPresenceService;

    private MemberKickedMessageListener listener;

    @BeforeEach
    void setUp() {
        listener = new MemberKickedMessageListener(messageService, roomPresenceService);
    }

    @Test
    @DisplayName("이벤트 처리 - Redis 정리 + 시스템 메시지 전송")
    void handleRemovesPresenceAndSendsSystemMessage() {
        MemberKickedEvent event = new MemberKickedEvent(ROOM_ID, 2L, "타겟", "https://img/target.jpg");

        listener.handle(event);

        then(roomPresenceService).should().removeAllSessions(ROOM_ID, 2L);
        then(messageService).should().sendMemberKickedSystemMessage(
                ROOM_ID, 2L, "타겟", "https://img/target.jpg");
    }

    @Test
    @DisplayName("Redis 실패 시에도 시스템 메시지 정상 전송")
    void handleSendsMessageEvenWhenRedisFails() {
        MemberKickedEvent event = new MemberKickedEvent(ROOM_ID, 2L, "타겟", "https://img/target.jpg");

        doThrow(new RuntimeException("Redis connection refused"))
                .when(roomPresenceService).removeAllSessions(ROOM_ID, 2L);

        listener.handle(event);

        then(messageService).should().sendMemberKickedSystemMessage(
                ROOM_ID, 2L, "타겟", "https://img/target.jpg");
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.messages.listener.MemberKickedMessageListenerTest"`
Expected: FAIL — 클래스가 없음

- [ ] **Step 3: MemberKickedMessageListener 구현**

```java
package com.howaboutus.backend.messages.listener;

import com.howaboutus.backend.messages.service.MessageService;
import com.howaboutus.backend.realtime.event.MemberKickedEvent;
import com.howaboutus.backend.realtime.service.RoomPresenceService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberKickedMessageListener {

    private final MessageService messageService;
    private final RoomPresenceService roomPresenceService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(MemberKickedEvent event) {
        removePresenceSafe(event.roomId(), event.kickedUserId());
        messageService.sendMemberKickedSystemMessage(
                event.roomId(), event.kickedUserId(),
                event.nickname(), event.profileImageUrl());
    }

    private void removePresenceSafe(UUID roomId, long userId) {
        try {
            roomPresenceService.removeAllSessions(roomId, userId);
        } catch (Exception e) {
            log.warn("Redis 접속 상태 제거 실패: roomId={}, userId={}", roomId, userId, e);
        }
    }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.messages.listener.MemberKickedMessageListenerTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/messages/listener/MemberKickedMessageListener.java \
        src/test/java/com/howaboutus/backend/messages/listener/MemberKickedMessageListenerTest.java
git commit -m "feat: MemberKickedMessageListener 구현 및 단위 테스트"
```
