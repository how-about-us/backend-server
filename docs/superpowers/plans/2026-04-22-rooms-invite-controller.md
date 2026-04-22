# Rooms 초대 & 입장 Part 3 — 컨트롤러 DTO + 엔드포인트 + 테스트

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 초대/입장 6개 API의 컨트롤러 DTO, 엔드포인트, 컨트롤러 테스트를 구현하고 전체 빌드를 검증한다.

**Architecture:** 기존 `RoomController`에 엔드포인트를 추가하고, Part 2에서 구현한 `RoomInviteService`를 주입한다.

**Tech Stack:** Spring Boot 4.0, Spring Data JPA, Hibernate 7, Java 21, Lombok, JUnit 5 + Mockito + MockMvc

**참조:** `docs/superpowers/specs/2026-04-19-rooms-design.md` 5-2절, 5-3절

**시리즈:**
- rooms-invite-infra: 인프라 + DTO + 초대 코드 재발급
- rooms-invite-join-approve: 입장 요청 + 상태 조회 + 승인/거절 서비스
- **rooms-invite-controller (현재):** 컨트롤러 DTO + 엔드포인트 + 컨트롤러 테스트

**선행 조건:** rooms-invite-infra, rooms-invite-join-approve 완료 — `RoomInviteService`의 모든 메서드와 서비스 DTO가 존재해야 한다.

---

## File Structure

| Action | Path | 역할 |
|--------|------|------|
| Create | `rooms/controller/dto/JoinRequest.java` | `POST /rooms/join` 요청 DTO |
| Create | `rooms/controller/dto/JoinResponse.java` | 입장 요청 응답 DTO |
| Create | `rooms/controller/dto/JoinStatusResponse.java` | 입장 상태 조회 응답 DTO |
| Create | `rooms/controller/dto/JoinRequestListResponse.java` | 대기 요청 목록 응답 DTO |
| Create | `rooms/controller/dto/InviteCodeResponse.java` | 초대 코드 재발급 응답 DTO |
| Modify | `rooms/controller/RoomController.java` | 6개 엔드포인트 추가 |
| Modify | `test/.../rooms/controller/RoomControllerTest.java` | 컨트롤러 테스트 추가 |

> 경로 프리픽스: `src/main/java/com/howaboutus/backend/`
> 테스트 프리픽스: `src/test/java/com/howaboutus/backend/`

---

### Task 1: 컨트롤러 DTO 생성

**Files:**
- Create: `rooms/controller/dto/JoinRequest.java`
- Create: `rooms/controller/dto/JoinResponse.java`
- Create: `rooms/controller/dto/JoinStatusResponse.java`
- Create: `rooms/controller/dto/JoinRequestListResponse.java`
- Create: `rooms/controller/dto/InviteCodeResponse.java`

- [ ] **Step 1: JoinRequest (요청 DTO)**

```java
package com.howaboutus.backend.rooms.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinRequest(
        @NotBlank(message = "초대 코드는 필수입니다")
        String inviteCode
) {
}
```

- [ ] **Step 2: JoinResponse (입장 요청 응답)**

```java
package com.howaboutus.backend.rooms.controller.dto;

import com.howaboutus.backend.rooms.service.dto.JoinResult;
import java.util.UUID;

public record JoinResponse(
        String status,
        UUID id,
        String roomTitle,
        String role
) {
    public static JoinResponse from(JoinResult result) {
        return new JoinResponse(
                result.status(),
                result.roomId(),
                result.roomTitle(),
                result.role() != null ? result.role().name() : null);
    }
}
```

- [ ] **Step 3: JoinStatusResponse**

```java
package com.howaboutus.backend.rooms.controller.dto;

import com.howaboutus.backend.rooms.service.dto.JoinStatusResult;
import java.util.UUID;

public record JoinStatusResponse(
        String status,
        UUID id,
        String roomTitle,
        String role
) {
    public static JoinStatusResponse from(JoinStatusResult result) {
        return new JoinStatusResponse(
                result.status(),
                result.roomId(),
                result.roomTitle(),
                result.role() != null ? result.role().name() : null);
    }
}
```

- [ ] **Step 4: JoinRequestListResponse**

```java
package com.howaboutus.backend.rooms.controller.dto;

import com.howaboutus.backend.rooms.service.dto.JoinRequestResult;
import java.time.Instant;
import java.util.List;

public record JoinRequestListResponse(
        List<JoinRequestItem> requests
) {
    public record JoinRequestItem(
            Long requestId,
            Long userId,
            String nickname,
            String profileImageUrl,
            Instant requestedAt
    ) {
    }

    public static JoinRequestListResponse from(List<JoinRequestResult> results) {
        List<JoinRequestItem> items = results.stream()
                .map(r -> new JoinRequestItem(
                        r.requestId(), r.userId(), r.nickname(),
                        r.profileImageUrl(), r.requestedAt()))
                .toList();
        return new JoinRequestListResponse(items);
    }
}
```

- [ ] **Step 5: InviteCodeResponse**

```java
package com.howaboutus.backend.rooms.controller.dto;

public record InviteCodeResponse(
        String inviteCode
) {
}
```

- [ ] **Step 6: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/controller/dto/JoinRequest.java \
       src/main/java/com/howaboutus/backend/rooms/controller/dto/JoinResponse.java \
       src/main/java/com/howaboutus/backend/rooms/controller/dto/JoinStatusResponse.java \
       src/main/java/com/howaboutus/backend/rooms/controller/dto/JoinRequestListResponse.java \
       src/main/java/com/howaboutus/backend/rooms/controller/dto/InviteCodeResponse.java
git commit -m "feat: 초대/입장 컨트롤러 DTO 추가"
```

---

### Task 2: RoomController에 6개 엔드포인트 추가 + 컨트롤러 테스트

**Files:**
- Modify: `rooms/controller/RoomController.java`
- Modify: `test/.../rooms/controller/RoomControllerTest.java`

- [ ] **Step 1: RoomController에 RoomInviteService 주입 + 엔드포인트 추가**

`RoomController.java`에 필드 추가:

```java
private final RoomInviteService roomInviteService;
```

import 추가:

```java
import com.howaboutus.backend.rooms.controller.dto.InviteCodeResponse;
import com.howaboutus.backend.rooms.controller.dto.JoinRequest;
import com.howaboutus.backend.rooms.controller.dto.JoinRequestListResponse;
import com.howaboutus.backend.rooms.controller.dto.JoinResponse;
import com.howaboutus.backend.rooms.controller.dto.JoinStatusResponse;
import com.howaboutus.backend.rooms.service.RoomInviteService;
import com.howaboutus.backend.rooms.service.dto.JoinResult;
```

엔드포인트 메서드 6개 추가:

```java
@Operation(summary = "초대 코드 재발급", description = "새 초대 코드를 발급합니다. HOST만 가능합니다.")
@PostMapping("/{roomId}/invite-code")
public InviteCodeResponse regenerateInviteCode(
        @RequestHeader("X-User-Id") Long userId,
        @PathVariable UUID roomId
) {
    return new InviteCodeResponse(roomInviteService.regenerateInviteCode(roomId, userId));
}

@Operation(summary = "초대 코드로 입장 요청", description = "초대 코드를 사용해 방 입장을 요청합니다.")
@PostMapping("/join")
public ResponseEntity<JoinResponse> requestJoin(
        @RequestHeader("X-User-Id") Long userId,
        @RequestBody @Valid JoinRequest request
) {
    JoinResult result = roomInviteService.requestJoin(request.inviteCode(), userId);
    HttpStatus status = "already_member".equals(result.status()) ? HttpStatus.OK : HttpStatus.ACCEPTED;
    return ResponseEntity.status(status).body(JoinResponse.from(result));
}

@Operation(summary = "입장 상태 조회", description = "초대 코드로 요청한 입장의 현재 상태를 확인합니다.")
@GetMapping("/join/status")
public JoinStatusResponse getJoinStatus(
        @RequestHeader("X-User-Id") Long userId,
        @RequestParam String inviteCode
) {
    return JoinStatusResponse.from(roomInviteService.getJoinStatus(inviteCode, userId));
}

@Operation(summary = "대기 중인 입장 요청 목록", description = "방의 입장 대기 요청 목록을 조회합니다. HOST만 가능합니다.")
@GetMapping("/{roomId}/join-requests")
public JoinRequestListResponse getJoinRequests(
        @RequestHeader("X-User-Id") Long userId,
        @PathVariable UUID roomId
) {
    return JoinRequestListResponse.from(roomInviteService.getJoinRequests(roomId, userId));
}

@Operation(summary = "입장 승인", description = "대기 중인 입장 요청을 승인합니다. HOST만 가능합니다.")
@PostMapping("/{roomId}/join-requests/{requestId}/approve")
public ResponseEntity<Void> approveJoinRequest(
        @RequestHeader("X-User-Id") Long userId,
        @PathVariable UUID roomId,
        @PathVariable Long requestId
) {
    roomInviteService.approve(roomId, requestId, userId);
    return ResponseEntity.ok().build();
}

@Operation(summary = "입장 거절", description = "대기 중인 입장 요청을 거절합니다. HOST만 가능합니다.")
@PostMapping("/{roomId}/join-requests/{requestId}/reject")
public ResponseEntity<Void> rejectJoinRequest(
        @RequestHeader("X-User-Id") Long userId,
        @PathVariable UUID roomId,
        @PathVariable Long requestId
) {
    roomInviteService.reject(roomId, requestId, userId);
    return ResponseEntity.ok().build();
}
```

- [ ] **Step 2: RoomControllerTest에 RoomInviteService Mock 추가**

```java
import com.howaboutus.backend.rooms.service.RoomInviteService;
import com.howaboutus.backend.rooms.service.dto.JoinResult;
import com.howaboutus.backend.rooms.service.dto.JoinStatusResult;
import com.howaboutus.backend.rooms.service.dto.JoinRequestResult;

@MockitoBean
private RoomInviteService roomInviteService;
```

- [ ] **Step 3: 컨트롤러 테스트 — 초대 코드 재발급**

```java
@Test
@DisplayName("초대 코드 재발급 성공 시 200을 반환한다")
void regenerateInviteCodeReturns200() throws Exception {
    given(roomInviteService.regenerateInviteCode(ROOM_ID, USER_ID))
            .willReturn("newCode1234");

    mockMvc.perform(post("/rooms/{roomId}/invite-code", ROOM_ID)
                    .header("X-User-Id", USER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inviteCode").value("newCode1234"));
}
```

- [ ] **Step 4: 컨트롤러 테스트 — 입장 요청**

```java
@Test
@DisplayName("입장 요청 성공 시 202를 반환한다")
void requestJoinReturns202() throws Exception {
    given(roomInviteService.requestJoin("aB3xK9mQ2w", USER_ID))
            .willReturn(JoinResult.pending("부산 여행"));

    mockMvc.perform(post("/rooms/join")
                    .header("X-User-Id", USER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"inviteCode":"aB3xK9mQ2w"}
                            """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("pending"))
            .andExpect(jsonPath("$.roomTitle").value("부산 여행"));
}

@Test
@DisplayName("이미 멤버인 사용자가 입장 요청하면 200을 반환한다")
void requestJoinReturns200WhenAlreadyMember() throws Exception {
    given(roomInviteService.requestJoin("aB3xK9mQ2w", USER_ID))
            .willReturn(JoinResult.alreadyMember(ROOM_ID, "부산 여행", RoomRole.MEMBER));

    mockMvc.perform(post("/rooms/join")
                    .header("X-User-Id", USER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"inviteCode":"aB3xK9mQ2w"}
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("already_member"))
            .andExpect(jsonPath("$.role").value("MEMBER"));
}
```

- [ ] **Step 5: 컨트롤러 테스트 — 입장 상태 조회**

```java
@Test
@DisplayName("입장 상태 조회 시 200을 반환한다")
void getJoinStatusReturns200() throws Exception {
    given(roomInviteService.getJoinStatus("aB3xK9mQ2w", USER_ID))
            .willReturn(JoinStatusResult.pending("부산 여행"));

    mockMvc.perform(get("/rooms/join/status")
                    .header("X-User-Id", USER_ID)
                    .param("inviteCode", "aB3xK9mQ2w"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("pending"));
}
```

- [ ] **Step 6: 컨트롤러 테스트 — 대기 요청 목록**

```java
@Test
@DisplayName("대기 요청 목록 조회 시 200을 반환한다")
void getJoinRequestsReturns200() throws Exception {
    given(roomInviteService.getJoinRequests(ROOM_ID, USER_ID))
            .willReturn(List.of(new JoinRequestResult(
                    42L, 3L, "김철수", "http://img.png",
                    Instant.parse("2026-04-20T00:00:00Z"))));

    mockMvc.perform(get("/rooms/{roomId}/join-requests", ROOM_ID)
                    .header("X-User-Id", USER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requests[0].requestId").value(42))
            .andExpect(jsonPath("$.requests[0].nickname").value("김철수"));
}
```

- [ ] **Step 7: 컨트롤러 테스트 — 승인 / 거절**

```java
@Test
@DisplayName("입장 승인 성공 시 200을 반환한다")
void approveJoinRequestReturns200() throws Exception {
    mockMvc.perform(post("/rooms/{roomId}/join-requests/{requestId}/approve", ROOM_ID, 42)
                    .header("X-User-Id", USER_ID))
            .andExpect(status().isOk());

    then(roomInviteService).should().approve(ROOM_ID, 42L, USER_ID);
}

@Test
@DisplayName("입장 거절 성공 시 200을 반환한다")
void rejectJoinRequestReturns200() throws Exception {
    mockMvc.perform(post("/rooms/{roomId}/join-requests/{requestId}/reject", ROOM_ID, 42)
                    .header("X-User-Id", USER_ID))
            .andExpect(status().isOk());

    then(roomInviteService).should().reject(ROOM_ID, 42L, USER_ID);
}
```

- [ ] **Step 8: 전체 테스트 실행**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.*"`
Expected: ALL PASSED

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/controller/RoomController.java \
       src/test/java/com/howaboutus/backend/rooms/controller/RoomControllerTest.java
git commit -m "feat: 초대/입장 6개 API 엔드포인트 및 컨트롤러 테스트 추가"
```

---

### Task 3: 전체 빌드 검증

**Files:** 없음 (검증만)

- [ ] **Step 1: 전체 빌드**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 전체 테스트 확인**

Run: `./gradlew test`
Expected: ALL PASSED — 기존 테스트 포함 깨지지 않음

- [ ] **Step 3: (실패 시) 수정 후 재빌드**

빌드 또는 테스트 실패가 있으면 에러 메시지를 확인하고 해당 파일을 수정한 뒤 다시 `./gradlew build`를 실행한다.
