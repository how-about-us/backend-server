# Rooms 초대 & 입장 Part 1 — 인프라 + DTO + 초대 코드 재발급

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ErrorCode, Repository 메서드, 서비스 DTO를 준비하고, RoomInviteService의 초대 코드 재발급 기능을 구현한다.

**Architecture:** 새로운 `RoomInviteService`에 초대/입장 로직을 집중시키고, 서비스 레이어 DTO와 컨트롤러 DTO를 분리하는 기존 패턴을 따른다.

**Tech Stack:** Spring Boot 4.0, Spring Data JPA, Hibernate 7, Java 21, Lombok, JUnit 5 + Mockito

**참조:** `docs/superpowers/specs/2026-04-19-rooms-design.md` 5-2절, 6절

**시리즈:**
- **rooms-invite-infra (현재):** 인프라 + DTO + 초대 코드 재발급
- rooms-invite-join-approve: 입장 요청 + 상태 조회 + 승인/거절 서비스
- rooms-invite-controller: 컨트롤러 DTO + 엔드포인트 + 컨트롤러 테스트

---

## File Structure

| Action | Path | 역할 |
|--------|------|------|
| Modify | `common/error/ErrorCode.java` | `JOIN_REQUEST_NOT_FOUND` 추가 |
| Modify | `rooms/repository/RoomMemberRepository.java` | PENDING 조회 메서드 추가 |
| Create | `rooms/service/dto/JoinResult.java` | 입장 요청 결과 |
| Create | `rooms/service/dto/JoinStatusResult.java` | 입장 상태 조회 결과 |
| Create | `rooms/service/dto/JoinRequestResult.java` | 대기 요청 목록 항목 |
| Create | `rooms/service/RoomInviteService.java` | 초대 코드 재발급 |
| Create | `test/.../rooms/service/RoomInviteServiceTest.java` | 서비스 단위 테스트 |

> 이하 경로 프리픽스: `src/main/java/com/howaboutus/backend/`
> 테스트 프리픽스: `src/test/java/com/howaboutus/backend/`

---

### Task 1: ErrorCode 추가 + Repository 메서드 추가

**Files:**
- Modify: `common/error/ErrorCode.java`
- Modify: `rooms/repository/RoomMemberRepository.java`

- [ ] **Step 1: ErrorCode에 `JOIN_REQUEST_NOT_FOUND` 추가**

`ErrorCode.java`의 `// 404 NOT FOUND` 섹션에 추가:

```java
JOIN_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 입장 요청입니다"),
```

- [ ] **Step 2: RoomMemberRepository에 PENDING 조회 메서드 추가**

```java
@EntityGraph(attributePaths = "user")
List<RoomMember> findByRoom_IdAndRole(UUID roomId, RoomRole role);

Optional<RoomMember> findByIdAndRoom_Id(Long id, UUID roomId);
```

- `findByRoom_IdAndRole`: 대기 요청 목록 조회용 (roomId + PENDING). `@EntityGraph`로 User를 함께 로딩하여 N+1 방지
- `findByIdAndRoom_Id`: 승인/거절 시 requestId + roomId로 조회

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/error/ErrorCode.java \
       src/main/java/com/howaboutus/backend/rooms/repository/RoomMemberRepository.java
git commit -m "feat: 초대/입장에 필요한 ErrorCode, Repository 메서드 추가"
```

---

### Task 2: 서비스 레이어 DTO 생성

**Files:**
- Create: `rooms/service/dto/JoinResult.java`
- Create: `rooms/service/dto/JoinStatusResult.java`
- Create: `rooms/service/dto/JoinRequestResult.java`

- [ ] **Step 1: JoinResult 생성**

입장 요청의 결과를 표현한다. `status` 필드로 `already_member` / `pending`을 구분한다.

```java
package com.howaboutus.backend.rooms.service.dto;

import com.howaboutus.backend.rooms.entity.RoomRole;
import java.util.UUID;

public record JoinResult(
        String status,
        UUID roomId,
        String roomTitle,
        RoomRole role
) {
    public static JoinResult alreadyMember(UUID roomId, String title, RoomRole role) {
        return new JoinResult("already_member", roomId, title, role);
    }

    public static JoinResult pending(String title) {
        return new JoinResult("pending", null, title, null);
    }
}
```

- [ ] **Step 2: JoinStatusResult 생성**

입장 상태 조회 결과. 승인됨이면 방 정보 포함, 대기 중이면 title만.

```java
package com.howaboutus.backend.rooms.service.dto;

import com.howaboutus.backend.rooms.entity.RoomRole;
import java.util.UUID;

public record JoinStatusResult(
        String status,
        UUID roomId,
        String roomTitle,
        RoomRole role
) {
    public static JoinStatusResult approved(UUID roomId, String title, RoomRole role) {
        return new JoinStatusResult("approved", roomId, title, role);
    }

    public static JoinStatusResult pending(String title) {
        return new JoinStatusResult("pending", null, title, null);
    }
}
```

- [ ] **Step 3: JoinRequestResult 생성**

대기 중인 입장 요청 하나를 표현한다.

```java
package com.howaboutus.backend.rooms.service.dto;

import java.time.Instant;

public record JoinRequestResult(
        Long requestId,
        Long userId,
        String nickname,
        String profileImageUrl,
        Instant requestedAt
) {
}
```

- [ ] **Step 4: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/service/dto/JoinResult.java \
       src/main/java/com/howaboutus/backend/rooms/service/dto/JoinStatusResult.java \
       src/main/java/com/howaboutus/backend/rooms/service/dto/JoinRequestResult.java
git commit -m "feat: 초대/입장 서비스 레이어 DTO 추가"
```

---

### Task 3: RoomInviteService 구현 + 단위 테스트 — 초대 코드 재발급

**Files:**
- Create: `rooms/service/RoomInviteService.java`
- Create: `test/.../rooms/service/RoomInviteServiceTest.java`

- [ ] **Step 1: RoomInviteService 뼈대 + 초대 코드 재발급 메서드 작성**

```java
package com.howaboutus.backend.rooms.service;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomInviteService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final InviteCodeGenerator inviteCodeGenerator;

    @Transactional
    public String regenerateInviteCode(UUID roomId, Long userId) {
        Room room = getActiveRoom(roomId);
        getHostMember(roomId, userId);
        String newCode = inviteCodeGenerator.generate();
        room.regenerateInviteCode(newCode);
        return newCode;
    }

    private Room getActiveRoom(UUID roomId) {
        return roomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
    }

    private RoomMember getActiveMember(UUID roomId, Long userId) {
        RoomMember member = roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_ROOM_MEMBER));
        if (member.getRole() == RoomRole.PENDING) {
            throw new CustomException(ErrorCode.NOT_ROOM_MEMBER);
        }
        return member;
    }

    private RoomMember getHostMember(UUID roomId, Long userId) {
        RoomMember member = getActiveMember(roomId, userId);
        if (member.getRole() != RoomRole.HOST) {
            throw new CustomException(ErrorCode.NOT_ROOM_HOST);
        }
        return member;
    }
}
```

- [ ] **Step 2: 초대 코드 재발급 테스트 작성**

```java
package com.howaboutus.backend.rooms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.user.entity.User;
import com.howaboutus.backend.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoomInviteServiceTest {

    @Mock private RoomRepository roomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private InviteCodeGenerator inviteCodeGenerator;

    private RoomInviteService roomInviteService;

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final Long HOST_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long STRANGER_ID = 99L;

    private Room room;
    private User hostUser;
    private User memberUser;
    private RoomMember hostMember;
    private RoomMember regularMember;

    @BeforeEach
    void setUp() {
        roomInviteService = new RoomInviteService(
                roomRepository, roomMemberRepository, inviteCodeGenerator);

        room = Room.create("부산 여행", "부산", null, null, "oldCode123", HOST_ID);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);

        hostUser = User.ofGoogle("google-host", "host@test.com", "호스트", null);
        ReflectionTestUtils.setField(hostUser, "id", HOST_ID);

        memberUser = User.ofGoogle("google-member", "member@test.com", "멤버", null);
        ReflectionTestUtils.setField(memberUser, "id", MEMBER_ID);

        hostMember = RoomMember.of(room, hostUser, RoomRole.HOST);
        regularMember = RoomMember.of(room, memberUser, RoomRole.MEMBER);
    }

    @Test
    @DisplayName("HOST가 초대 코드를 재발급하면 새 코드를 반환한다")
    void regenerateInviteCodeSuccess() {
        given(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, HOST_ID))
                .willReturn(Optional.of(hostMember));
        given(inviteCodeGenerator.generate()).willReturn("newCode9876");

        String result = roomInviteService.regenerateInviteCode(ROOM_ID, HOST_ID);

        assertThat(result).isEqualTo("newCode9876");
        assertThat(room.getInviteCode()).isEqualTo("newCode9876");
    }

    @Test
    @DisplayName("MEMBER가 초대 코드 재발급하면 NOT_ROOM_HOST 예외")
    void regenerateInviteCodeThrowsWhenNotHost() {
        given(roomRepository.findByIdAndDeletedAtIsNull(ROOM_ID)).willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, MEMBER_ID))
                .willReturn(Optional.of(regularMember));

        assertThatThrownBy(() -> roomInviteService.regenerateInviteCode(ROOM_ID, MEMBER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_ROOM_HOST);
    }
}
```

- [ ] **Step 3: 테스트 실행**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomInviteServiceTest"`
Expected: 2 tests PASSED

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/service/RoomInviteService.java \
       src/test/java/com/howaboutus/backend/rooms/service/RoomInviteServiceTest.java
git commit -m "feat: RoomInviteService 초대 코드 재발급 구현 및 테스트"
```
