# RoomController 리팩토링 (Part 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** RoomController의 `@RequestHeader("X-User-Id")` 방식을 `@AuthenticationPrincipal`로 교체하고, 테스트를 JWT 쿠키 인증 방식으로 전환한다.

**Architecture:** RoomController의 11개 메서드에서 `@RequestHeader("X-User-Id")`를 `@AuthenticationPrincipal`로 교체한다. RoomControllerTest는 `Cookie("access_token", "valid-jwt")` + `jwtProvider.extractUserId` mock 패턴으로 전환한다.

**Tech Stack:** Spring Security, JWT (쿠키 기반), Spring Boot 4.0.5, JUnit 5, MockMvc

**선행 Plan:** Part 1 — `docs/superpowers/plans/2026-04-25-rooms-auth-part1-security-config.md` (SecurityConfig `anyRequest().authenticated()` 전환 완료 필요)

---

## File Structure

| 파일 | 변경 유형 | 역할 |
|------|----------|------|
| `src/main/java/com/howaboutus/backend/rooms/controller/RoomController.java` | 수정 | `@AuthenticationPrincipal` 교체 |
| `src/test/java/com/howaboutus/backend/rooms/controller/RoomControllerTest.java` | 수정 | JWT 쿠키 인증 방식으로 전환 |

---

### Task 1: RoomController — @AuthenticationPrincipal로 교체

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/controller/RoomController.java`

- [ ] **Step 1: RoomController 수정**

`src/main/java/com/howaboutus/backend/rooms/controller/RoomController.java`에서:

**import 변경:**
- 제거: `import org.springframework.web.bind.annotation.RequestHeader;`
- 추가: `import org.springframework.security.core.annotation.AuthenticationPrincipal;`

**클래스 상단 TODO 주석 제거:**
```java
// 제거: // TODO: X-User-Id 헤더 → @AuthenticationPrincipal로 교체 (JWT 필터 SecurityFilterChain 연결 후)
```

**모든 메서드 파라미터 교체** — 11개 메서드의 `@RequestHeader("X-User-Id") Long userId`를 `@AuthenticationPrincipal Long userId`로 교체:

`create`, `getMyRooms`, `getDetail`, `update`, `delete`, `regenerateInviteCode`, `requestJoin`, `getJoinStatus`, `getJoinRequests`, `approveJoinRequest`, `rejectJoinRequest`

예시 (create 메서드):
```java
@PostMapping
public ResponseEntity<RoomDetailResponse> create(
        @AuthenticationPrincipal Long userId,
        @RequestBody @Valid CreateRoomRequest request
) {
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/controller/RoomController.java
git commit -m "refactor: RoomController X-User-Id 헤더를 @AuthenticationPrincipal로 교체"
```

---

### Task 2: RoomControllerTest — JWT 쿠키 인증 방식으로 전환

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/rooms/controller/RoomControllerTest.java`

- [ ] **Step 1: import 추가 및 상수/setup 추가**

import 추가:
```java
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
```

기존 상수 선언 아래에 상수 및 `@BeforeEach` 추가:
```java
private static final String VALID_TOKEN = "valid-jwt";

@BeforeEach
void setUp() {
    given(jwtProvider.extractUserId(VALID_TOKEN)).willReturn(USER_ID);
}
```

- [ ] **Step 2: 모든 테스트에서 헤더를 쿠키로 교체**

모든 테스트의 `.header("X-User-Id", USER_ID)`를 `.cookie(new Cookie("access_token", VALID_TOKEN))`로 교체한다. 대상 15개 테스트:

`createRoomReturns201`:
```java
mockMvc.perform(post("/rooms")
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"부산 여행","destination":"부산","startDate":"2026-05-01","endDate":"2026-05-03"}
                        """))
```

`createRoomReturns400WhenTitleMissing`:
```java
mockMvc.perform(post("/rooms")
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"destination":"부산"}
                        """))
```

`getDetailReturns200`:
```java
mockMvc.perform(get("/rooms/{roomId}", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN)))
```

`getDetailReturns403WhenNotMember`:
```java
mockMvc.perform(get("/rooms/{roomId}", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN)))
```

`getMyRoomsReturns200`:
```java
mockMvc.perform(get("/rooms")
                .cookie(new Cookie("access_token", VALID_TOKEN)))
```

`updateRoomReturns200`:
```java
mockMvc.perform(patch("/rooms/{roomId}", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"부산 맛집 여행"}
                        """))
```

`updateRoomReturns403WhenNotHost`:
```java
mockMvc.perform(patch("/rooms/{roomId}", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"변경"}
                        """))
```

`deleteRoomReturns204`:
```java
mockMvc.perform(delete("/rooms/{roomId}", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN)))
```

`regenerateInviteCodeReturns200`:
```java
mockMvc.perform(post("/rooms/{roomId}/invite-code", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN)))
```

`requestJoinReturns202`:
```java
mockMvc.perform(post("/rooms/join")
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"inviteCode":"aB3xK9mQ2w"}
                        """))
```

`requestJoinReturns200WhenAlreadyMember`:
```java
mockMvc.perform(post("/rooms/join")
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"inviteCode":"aB3xK9mQ2w"}
                        """))
```

`getJoinStatusReturns200`:
```java
mockMvc.perform(get("/rooms/{roomId}/join/status", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN)))
```

`getJoinRequestsReturns200`:
```java
mockMvc.perform(get("/rooms/{roomId}/join-requests", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN)))
```

`approveJoinRequestReturns200`:
```java
mockMvc.perform(post("/rooms/{roomId}/join-requests/{requestId}/approve", ROOM_ID, 42)
                .cookie(new Cookie("access_token", VALID_TOKEN)))
```

`rejectJoinRequestReturns200`:
```java
mockMvc.perform(post("/rooms/{roomId}/join-requests/{requestId}/reject", ROOM_ID, 42)
                .cookie(new Cookie("access_token", VALID_TOKEN)))
```

- [ ] **Step 3: RoomControllerTest 실행하여 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.controller.RoomControllerTest"`

Expected: 15개 테스트 모두 PASS

- [ ] **Step 4: 커밋**

```bash
git add src/test/java/com/howaboutus/backend/rooms/controller/RoomControllerTest.java
git commit -m "test: RoomControllerTest를 JWT 쿠키 인증 방식으로 전환"
```

---

### Task 3: 전체 테스트 통과 확인

- [ ] **Step 1: 전체 테스트 실행**

Run: `./gradlew test`

Expected: BUILD SUCCESSFUL — 모든 테스트 통과

- [ ] **Step 2: (실패 시) 실패한 테스트 분석 및 수정**

예상하지 못한 테스트 실패가 있으면 동일한 패턴(JWT 쿠키 추가)으로 수정한다.
