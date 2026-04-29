### Task 3: RoomPresenceService — removeAllSessions() 추가 (TDD)

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/realtime/service/RoomPresenceService.java`
- Test: `src/test/java/com/howaboutus/backend/realtime/service/RoomPresenceServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`RoomPresenceServiceTest.java` 파일이 이미 존재하면 확장, 없으면 새로 생성:

```java
package com.howaboutus.backend.realtime.service;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.given;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RoomPresenceServiceTest {

    private static final UUID ROOM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long USER_ID = 1L;

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private SetOperations<String, String> setOperations;

    private RoomPresenceService roomPresenceService;

    @BeforeEach
    void setUp() {
        roomPresenceService = new RoomPresenceService(redisTemplate);
    }

    @Test
    @DisplayName("removeAllSessions - 세션 키 삭제 + connected_users에서 제거")
    void removeAllSessionsDeletesKeysAndRemovesFromSet() {
        given(redisTemplate.opsForSet()).willReturn(setOperations);

        roomPresenceService.removeAllSessions(ROOM_ID, USER_ID);

        String userSessionsKey = "room:" + ROOM_ID + ":sessions:" + USER_ID;
        String connectedUsersKey = "room:" + ROOM_ID + ":connected_users";

        then(redisTemplate).should().delete(userSessionsKey);
        then(setOperations).should().remove(connectedUsersKey, String.valueOf(USER_ID));
    }

    @Test
    @DisplayName("removeAllSessions - 세션 없는 유저도 에러 없이 정상 처리")
    void removeAllSessionsHandlesNonExistentUser() {
        given(redisTemplate.opsForSet()).willReturn(setOperations);

        roomPresenceService.removeAllSessions(ROOM_ID, 999L);

        String userSessionsKey = "room:" + ROOM_ID + ":sessions:999";
        String connectedUsersKey = "room:" + ROOM_ID + ":connected_users";

        then(redisTemplate).should().delete(userSessionsKey);
        then(setOperations).should().remove(connectedUsersKey, "999");
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.realtime.service.RoomPresenceServiceTest"`
Expected: FAIL — `removeAllSessions` 메서드가 없음

- [ ] **Step 3: removeAllSessions 구현**

`RoomPresenceService.java`에 메서드 추가:

```java
public void removeAllSessions(UUID roomId, Long userId) {
    redisTemplate.delete(userSessionsKey(roomId, userId));
    redisTemplate.opsForSet().remove(connectedUsersKey(roomId), String.valueOf(userId));
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.realtime.service.RoomPresenceServiceTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/realtime/service/RoomPresenceService.java \
        src/test/java/com/howaboutus/backend/realtime/service/RoomPresenceServiceTest.java
git commit -m "feat: RoomPresenceService.removeAllSessions() 구현 및 테스트"
```
