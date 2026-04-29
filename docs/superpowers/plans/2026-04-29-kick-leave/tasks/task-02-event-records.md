### Task 2: 이벤트 Record 생성

**Files:**
- Create: `src/main/java/com/howaboutus/backend/realtime/event/MemberKickedEvent.java`
- Create: `src/main/java/com/howaboutus/backend/realtime/event/MemberLeftEvent.java`

- [ ] **Step 1: MemberKickedEvent 생성**

```java
package com.howaboutus.backend.realtime.event;

import java.util.UUID;

public record MemberKickedEvent(
        UUID roomId,
        long kickedUserId,
        String nickname,
        String profileImageUrl
) {}
```

- [ ] **Step 2: MemberLeftEvent 생성**

```java
package com.howaboutus.backend.realtime.event;

import java.util.UUID;

public record MemberLeftEvent(
        UUID roomId,
        long leftUserId,
        String nickname,
        String profileImageUrl
) {}
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/realtime/event/MemberKickedEvent.java \
        src/main/java/com/howaboutus/backend/realtime/event/MemberLeftEvent.java
git commit -m "feat: MemberKickedEvent, MemberLeftEvent record 추가"
```
