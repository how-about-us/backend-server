# 방 멤버 목록 조회 API 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 방에 참여 중인 멤버(HOST/MEMBER) 목록을 Redis 접속 상태와 함께 조회하는 REST API 구현

**Architecture:** `RoomController` → `RoomMemberService` → (`RoomAuthorizationService` + `RoomMemberRepository` + `RoomPresenceService`). 서비스에서 DB 멤버 목록과 Redis 온라인 상태를 조합하여 반환. Redis 장애 시 모든 멤버를 offline으로 graceful degradation.

**Tech Stack:** Spring Boot 4, Spring Data JPA, Redis (StringRedisTemplate), JUnit 5, Mockito, MockMvc

---

## 파일 구조

| 액션 | 파일 경로 | 역할 |
|------|-----------|------|
| Create | `src/main/java/.../rooms/service/dto/RoomMemberResult.java` | 서비스 레이어 반환 DTO |
| Create | `src/main/java/.../rooms/service/RoomMemberService.java` | 멤버 목록 조회 비즈니스 로직 |
| Create | `src/main/java/.../rooms/controller/dto/RoomMemberResponse.java` | 단일 멤버 응답 DTO |
| Create | `src/main/java/.../rooms/controller/dto/RoomMemberListResponse.java` | wrapper DTO |
| Modify | `src/main/java/.../rooms/repository/RoomMemberRepository.java` | `findByRoom_IdAndRoleIn` 추가 |
| Modify | `src/main/java/.../rooms/controller/RoomController.java` | `GET /{roomId}/members` 엔드포인트 추가 |
| Create | `src/test/java/.../rooms/service/RoomMemberServiceTest.java` | 서비스 단위 테스트 |
| Modify | `src/test/java/.../rooms/controller/RoomControllerTest.java` | 컨트롤러 테스트 추가 |

> 경로 prefix: `com/howaboutus/backend`

---

### Task 1: Repository 메서드 추가

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/repository/RoomMemberRepository.java`

- [ ] **Step 1: `findByRoom_IdAndRoleIn` 메서드 추가**

```java
@EntityGraph(attributePaths = "user")
List<RoomMember> findByRoom_IdAndRoleIn(UUID roomId, List<RoomRole> roles);
```

기존 `findByRoom_IdAndRole` (단일 role) 아래에 추가한다. `@EntityGraph(attributePaths = "user")`로 User를 JOIN해서 N+1을 방지한다.

- [ ] **Step 2: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/repository/RoomMemberRepository.java
git commit -m "feat: RoomMemberRepository에 findByRoom_IdAndRoleIn 메서드 추가"
```

---

### Task 2: 서비스 DTO 생성

**Files:**
- Create: `src/main/java/com/howaboutus/backend/rooms/service/dto/RoomMemberResult.java`

- [ ] **Step 1: `RoomMemberResult` record 작성**

```java
package com.howaboutus.backend.rooms.service.dto;

import com.howaboutus.backend.rooms.entity.RoomRole;
import java.time.Instant;

public record RoomMemberResult(
        Long userId,
        String nickname,
        String profileImageUrl,
        RoomRole role,
        boolean isOnline,
        Instant joinedAt
) {
}
```

- [ ] **Step 2: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/service/dto/RoomMemberResult.java
git commit -m "feat: RoomMemberResult 서비스 DTO 추가"
```

---

### Task 3: RoomMemberService 구현 + 단위 테스트

**Files:**
- Create: `src/main/java/com/howaboutus/backend/rooms/service/RoomMemberService.java`
- Create: `src/test/java/com/howaboutus/backend/rooms/service/RoomMemberServiceTest.java`

- [ ] **Step 1: 테스트 파일 생성 — 정상 조회 테스트**

```java
package com.howaboutus.backend.rooms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.realtime.service.RoomPresenceService;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.service.dto.RoomMemberResult;
import com.howaboutus.backend.user.entity.User;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoomMemberServiceTest {

    private static final UUID ROOM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long USER_ID = 1L;
    private static final List<RoomRole> ACTIVE_ROLES = List.of(RoomRole.HOST, RoomRole.MEMBER);

    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private RoomPresenceService roomPresenceService;
    @Mock private RoomAuthorizationService roomAuthorizationService;

    private RoomMemberService roomMemberService;

    @BeforeEach
    void setUp() {
        roomMemberService = new RoomMemberService(
                roomMemberRepository, roomPresenceService, roomAuthorizationService);
    }

    @Test
    @DisplayName("HOST와 MEMBER를 조회하고 온라인 상태를 매핑한다")
    void getMembersReturnsActiveMembers() {
        // given
        User host = User.ofGoogle("g1", "host@test.com", "호스트", "https://img/host.jpg");
        ReflectionTestUtils.setField(host, "id", 1L);
        User member = User.ofGoogle("g2", "member@test.com", "멤버", null);
        ReflectionTestUtils.setField(member, "id", 2L);

        Room room = Room.create("여행", "부산", null, null, "invite1", 1L);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);

        RoomMember hostMember = RoomMember.of(room, host, RoomRole.HOST);
        RoomMember regularMember = RoomMember.of(room, member, RoomRole.MEMBER);

        given(roomAuthorizationService.requireActiveMember(ROOM_ID, USER_ID)).willReturn(hostMember);
        given(roomMemberRepository.findByRoom_IdAndRoleIn(ROOM_ID, ACTIVE_ROLES))
                .willReturn(List.of(hostMember, regularMember));
        given(roomPresenceService.getOnlineUserIds(ROOM_ID)).willReturn(Set.of(1L));

        // when
        List<RoomMemberResult> results = roomMemberService.getMembers(ROOM_ID, USER_ID);

        // then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).userId()).isEqualTo(1L);
        assertThat(results.get(0).isOnline()).isTrue();
        assertThat(results.get(0).role()).isEqualTo(RoomRole.HOST);
        assertThat(results.get(0).nickname()).isEqualTo("호스트");

        assertThat(results.get(1).userId()).isEqualTo(2L);
        assertThat(results.get(1).isOnline()).isFalse();
    }

    @Test
    @DisplayName("비멤버 접근 시 예외를 던진다")
    void getMembersThrowsForNonMember() {
        given(roomAuthorizationService.requireActiveMember(ROOM_ID, USER_ID))
                .willThrow(new CustomException(ErrorCode.NOT_ROOM_MEMBER));

        assertThatThrownBy(() -> roomMemberService.getMembers(ROOM_ID, USER_ID))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("Redis 장애 시 모든 멤버를 offline으로 처리한다")
    void getMembersHandlesRedisFailure() {
        User host = User.ofGoogle("g1", "host@test.com", "호스트", null);
        ReflectionTestUtils.setField(host, "id", 1L);

        Room room = Room.create("여행", "부산", null, null, "invite1", 1L);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);

        RoomMember hostMember = RoomMember.of(room, host, RoomRole.HOST);

        given(roomAuthorizationService.requireActiveMember(ROOM_ID, USER_ID)).willReturn(hostMember);
        given(roomMemberRepository.findByRoom_IdAndRoleIn(ROOM_ID, ACTIVE_ROLES))
                .willReturn(List.of(hostMember));
        given(roomPresenceService.getOnlineUserIds(ROOM_ID))
                .willThrow(new RuntimeException("Redis connection refused"));

        List<RoomMemberResult> results = roomMemberService.getMembers(ROOM_ID, USER_ID);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isOnline()).isFalse();
    }

    @Test
    @DisplayName("멤버가 없으면 빈 리스트를 반환한다")
    void getMembersReturnsEmptyList() {
        RoomMember dummyMember = createDummyMember();
        given(roomAuthorizationService.requireActiveMember(ROOM_ID, USER_ID)).willReturn(dummyMember);
        given(roomMemberRepository.findByRoom_IdAndRoleIn(ROOM_ID, ACTIVE_ROLES))
                .willReturn(List.of());
        given(roomPresenceService.getOnlineUserIds(ROOM_ID)).willReturn(Set.of());

        List<RoomMemberResult> results = roomMemberService.getMembers(ROOM_ID, USER_ID);

        assertThat(results).isEmpty();
    }

    private RoomMember createDummyMember() {
        User user = User.ofGoogle("g1", "test@test.com", "테스터", null);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        Room room = Room.create("여행", "부산", null, null, "invite1", USER_ID);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        return RoomMember.of(room, user, RoomRole.HOST);
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomMemberServiceTest" 2>&1 | tail -5
```

Expected: `RoomMemberService`가 존재하지 않으므로 컴파일 에러

- [ ] **Step 3: `RoomMemberService` 구현**

```java
package com.howaboutus.backend.rooms.service;

import com.howaboutus.backend.realtime.service.RoomPresenceService;
import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.service.dto.RoomMemberResult;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomMemberService {

    private static final List<RoomRole> ACTIVE_ROLES = List.of(RoomRole.HOST, RoomRole.MEMBER);

    private final RoomMemberRepository roomMemberRepository;
    private final RoomPresenceService roomPresenceService;
    private final RoomAuthorizationService roomAuthorizationService;

    public List<RoomMemberResult> getMembers(UUID roomId, Long userId) {
        roomAuthorizationService.requireActiveMember(roomId, userId);

        List<RoomMember> members = roomMemberRepository.findByRoom_IdAndRoleIn(roomId, ACTIVE_ROLES);
        Set<Long> onlineUserIds = getOnlineUserIdsSafe(roomId);

        return members.stream()
                .map(m -> new RoomMemberResult(
                        m.getUser().getId(),
                        m.getUser().getNickname(),
                        m.getUser().getProfileImageUrl(),
                        m.getRole(),
                        onlineUserIds.contains(m.getUser().getId()),
                        m.getJoinedAt()))
                .toList();
    }

    private Set<Long> getOnlineUserIdsSafe(UUID roomId) {
        try {
            return roomPresenceService.getOnlineUserIds(roomId);
        } catch (Exception e) {
            log.warn("Redis 접속 상태 조회 실패, 모든 멤버를 offline으로 처리: roomId={}", roomId, e);
            return Collections.emptySet();
        }
    }
}
```

- [ ] **Step 4: 테스트 실행 — 전체 통과 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomMemberServiceTest" 2>&1 | tail -10
```

Expected: 4개 테스트 모두 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/service/RoomMemberService.java \
       src/test/java/com/howaboutus/backend/rooms/service/RoomMemberServiceTest.java
git commit -m "feat: RoomMemberService 구현 및 단위 테스트"
```

---

### Task 4: Controller DTO 생성

**Files:**
- Create: `src/main/java/com/howaboutus/backend/rooms/controller/dto/RoomMemberResponse.java`
- Create: `src/main/java/com/howaboutus/backend/rooms/controller/dto/RoomMemberListResponse.java`

- [ ] **Step 1: `RoomMemberResponse` 작성**

```java
package com.howaboutus.backend.rooms.controller.dto;

import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.service.dto.RoomMemberResult;
import java.time.Instant;

public record RoomMemberResponse(
        Long userId,
        String nickname,
        String profileImageUrl,
        RoomRole role,
        boolean isOnline,
        Instant joinedAt
) {
    public static RoomMemberResponse from(RoomMemberResult result) {
        return new RoomMemberResponse(
                result.userId(),
                result.nickname(),
                result.profileImageUrl(),
                result.role(),
                result.isOnline(),
                result.joinedAt());
    }
}
```

- [ ] **Step 2: `RoomMemberListResponse` 작성**

```java
package com.howaboutus.backend.rooms.controller.dto;

import com.howaboutus.backend.rooms.service.dto.RoomMemberResult;
import java.util.List;

public record RoomMemberListResponse(
        List<RoomMemberResponse> members
) {
    public static RoomMemberListResponse from(List<RoomMemberResult> results) {
        List<RoomMemberResponse> members = results.stream()
                .map(RoomMemberResponse::from)
                .toList();
        return new RoomMemberListResponse(members);
    }
}
```

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/controller/dto/RoomMemberResponse.java \
       src/main/java/com/howaboutus/backend/rooms/controller/dto/RoomMemberListResponse.java
git commit -m "feat: RoomMemberResponse, RoomMemberListResponse 컨트롤러 DTO 추가"
```

---

### Task 5: Controller 엔드포인트 추가 + 테스트

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/controller/RoomController.java`
- Modify: `src/test/java/com/howaboutus/backend/rooms/controller/RoomControllerTest.java`

- [ ] **Step 1: `RoomControllerTest`에 테스트 추가**

기존 테스트 파일 하단에 다음 테스트를 추가한다. `RoomMemberService`에 대한 `@MockitoBean` 선언도 기존 `@MockitoBean` 필드 옆에 추가한다.

```java
// 기존 @MockitoBean 필드 옆에 추가
@MockitoBean
private RoomMemberService roomMemberService;

// import 추가
// import com.howaboutus.backend.rooms.service.RoomMemberService;
// import com.howaboutus.backend.rooms.service.dto.RoomMemberResult;
// import com.howaboutus.backend.rooms.entity.RoomRole;

// 테스트 메서드 추가
@Test
@DisplayName("멤버 목록 조회 성공 시 200을 반환한다")
void getMembersReturns200() throws Exception {
    List<RoomMemberResult> results = List.of(
            new RoomMemberResult(1L, "호스트", "https://img/host.jpg", RoomRole.HOST, true,
                    Instant.parse("2026-04-20T00:00:00Z")),
            new RoomMemberResult(2L, "멤버", null, RoomRole.MEMBER, false,
                    Instant.parse("2026-04-21T00:00:00Z"))
    );
    given(roomMemberService.getMembers(ROOM_ID, USER_ID)).willReturn(results);

    mockMvc.perform(get("/rooms/{roomId}/members", ROOM_ID)
                    .cookie(new Cookie("access_token", VALID_TOKEN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members").isArray())
            .andExpect(jsonPath("$.members.length()").value(2))
            .andExpect(jsonPath("$.members[0].userId").value(1))
            .andExpect(jsonPath("$.members[0].nickname").value("호스트"))
            .andExpect(jsonPath("$.members[0].role").value("HOST"))
            .andExpect(jsonPath("$.members[0].isOnline").value(true))
            .andExpect(jsonPath("$.members[1].userId").value(2))
            .andExpect(jsonPath("$.members[1].isOnline").value(false));
}

@Test
@DisplayName("비멤버가 멤버 목록 조회 시 403을 반환한다")
void getMembersReturns403ForNonMember() throws Exception {
    given(roomMemberService.getMembers(ROOM_ID, USER_ID))
            .willThrow(new CustomException(ErrorCode.NOT_ROOM_MEMBER));

    mockMvc.perform(get("/rooms/{roomId}/members", ROOM_ID)
                    .cookie(new Cookie("access_token", VALID_TOKEN)))
            .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.rooms.controller.RoomControllerTest.getMembersReturns200" 2>&1 | tail -5
```

Expected: `RoomController`에 엔드포인트가 없으므로 404 또는 컴파일 에러

- [ ] **Step 3: `RoomController`에 엔드포인트 추가**

`RoomController`에 `RoomMemberService` 필드 주입과 엔드포인트를 추가한다.

```java
// 필드 추가 (기존 roomInviteService 아래)
private final RoomMemberService roomMemberService;

// 엔드포인트 추가
@Operation(summary = "멤버 목록 조회", description = "방의 멤버 목록과 접속 상태를 조회합니다. HOST 또는 MEMBER만 접근 가능합니다.")
@GetMapping("/{roomId}/members")
public RoomMemberListResponse getMembers(
        @AuthenticationPrincipal Long userId,
        @PathVariable UUID roomId
) {
    return RoomMemberListResponse.from(roomMemberService.getMembers(roomId, userId));
}
```

import도 추가:

```java
import com.howaboutus.backend.rooms.controller.dto.RoomMemberListResponse;
import com.howaboutus.backend.rooms.service.RoomMemberService;
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.rooms.controller.RoomControllerTest" 2>&1 | tail -10
```

Expected: 기존 테스트 포함 전체 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/controller/RoomController.java \
       src/test/java/com/howaboutus/backend/rooms/controller/RoomControllerTest.java
git commit -m "feat: GET /rooms/{roomId}/members 엔드포인트 추가"
```

---

### Task 6: 전체 테스트 확인 + 문서 갱신

**Files:**
- Modify: `docs/ai/features.md`

- [ ] **Step 1: 전체 테스트 실행**

```bash
./gradlew test 2>&1 | tail -15
```

Expected: 전체 PASS

- [ ] **Step 2: `features.md` 갱신**

`features.md`에서 "방 멤버 목록 조회" 항목을 `[x]`로 변경한다.
"현재 접속 중인 유저 조회" 항목을 `[x]`로 변경하고, 멤버 목록 API에 접속 상태가 포함됨을 주석으로 남긴다.

- [ ] **Step 3: 커밋**

```bash
git add docs/ai/features.md
git commit -m "docs: features.md 멤버 목록 조회 및 접속 유저 조회 완료 표기"
```
