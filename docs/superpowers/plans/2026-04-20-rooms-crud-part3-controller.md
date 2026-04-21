# Rooms CRUD Part 3: 컨트롤러 + 문서

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Controller DTO, RoomController, 컨트롤러 단위 테스트, features.md/erd.md 문서 갱신

**Architecture:** 기존 bookmarks 컨트롤러 패턴과 동일. 현재 인증이 permitAll 상태이므로 userId는 `X-User-Id` 헤더로 임시 처리.

**Tech Stack:** Spring Boot 4.0, Java 21, MockMvc, JUnit 5

**선행:** Part 1(엔티티) + Part 2(서비스) 완료 필수

**시리즈:** Part 1(엔티티) → Part 2(서비스) → **Part 3/3**

---

## Task 10: 컨트롤러 DTO

**Files:**
- Create: `src/main/java/com/howaboutus/backend/rooms/controller/dto/CreateRoomRequest.java`
- Create: `src/main/java/com/howaboutus/backend/rooms/controller/dto/UpdateRoomRequest.java`
- Create: `src/main/java/com/howaboutus/backend/rooms/controller/dto/RoomDetailResponse.java`
- Create: `src/main/java/com/howaboutus/backend/rooms/controller/dto/RoomListResponse.java`

- [ ] **Step 1: CreateRoomRequest**

```java
package com.howaboutus.backend.rooms.controller.dto;

import com.howaboutus.backend.rooms.service.dto.RoomCreateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateRoomRequest(
        @NotBlank(message = "방 제목은 필수입니다")
        @Size(max = 100, message = "방 제목은 100자 이하여야 합니다")
        String title,

        @Size(max = 200, message = "여행지는 200자 이하여야 합니다")
        String destination,

        LocalDate startDate,
        LocalDate endDate
) {
    public RoomCreateCommand toCommand() {
        return new RoomCreateCommand(title, destination, startDate, endDate);
    }
}
```

- [ ] **Step 2: UpdateRoomRequest**

```java
package com.howaboutus.backend.rooms.controller.dto;

import com.howaboutus.backend.rooms.service.dto.RoomUpdateCommand;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateRoomRequest(
        @Size(max = 100, message = "방 제목은 100자 이하여야 합니다")
        String title,

        @Size(max = 200, message = "여행지는 200자 이하여야 합니다")
        String destination,

        LocalDate startDate,
        LocalDate endDate
) {
    public RoomUpdateCommand toCommand() {
        return new RoomUpdateCommand(title, destination, startDate, endDate);
    }
}
```

- [ ] **Step 3: RoomDetailResponse**

```java
package com.howaboutus.backend.rooms.controller.dto;

import com.howaboutus.backend.rooms.service.dto.RoomDetailResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RoomDetailResponse(
        UUID id,
        String title,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        String inviteCode,
        long memberCount,
        String role,
        Instant createdAt
) {
    public static RoomDetailResponse from(RoomDetailResult result) {
        return new RoomDetailResponse(
                result.id(), result.title(), result.destination(),
                result.startDate(), result.endDate(), result.inviteCode(),
                result.memberCount(), result.role().name(), result.createdAt());
    }
}
```

- [ ] **Step 4: RoomListResponse**

```java
package com.howaboutus.backend.rooms.controller.dto;

import com.howaboutus.backend.rooms.service.dto.RoomListResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RoomListResponse(
        List<RoomSummary> rooms,
        Instant nextCursor,
        boolean hasNext
) {
    public record RoomSummary(
            UUID id,
            String title,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            long memberCount,
            String role,
            Instant joinedAt
    ) {
    }

    public static RoomListResponse from(RoomListResult result) {
        List<RoomSummary> rooms = result.rooms().stream()
                .map(r -> new RoomSummary(
                        r.id(), r.title(), r.destination(),
                        r.startDate(), r.endDate(),
                        r.memberCount(), r.role(), r.joinedAt()))
                .toList();
        return new RoomListResponse(rooms, result.nextCursor(), result.hasNext());
    }
}
```

- [ ] **Step 5: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/controller/dto/
git commit -m "feat: Room 컨트롤러 Request/Response DTO 추가"
```

---

## Task 11: RoomController

**Files:**
- Create: `src/main/java/com/howaboutus/backend/rooms/controller/RoomController.java`

- [ ] **Step 1: RoomController 작성**

```java
package com.howaboutus.backend.rooms.controller;

import com.howaboutus.backend.rooms.controller.dto.CreateRoomRequest;
import com.howaboutus.backend.rooms.controller.dto.RoomDetailResponse;
import com.howaboutus.backend.rooms.controller.dto.RoomListResponse;
import com.howaboutus.backend.rooms.controller.dto.UpdateRoomRequest;
import com.howaboutus.backend.rooms.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Rooms", description = "여행 방 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms")
public class RoomController {

    private final RoomService roomService;

    @Operation(summary = "방 생성", description = "새 여행 방을 생성합니다. 생성자는 자동으로 HOST가 됩니다.")
    @PostMapping
    public ResponseEntity<RoomDetailResponse> create(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid CreateRoomRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RoomDetailResponse.from(roomService.create(request.toCommand(), userId)));
    }

    @Operation(summary = "내 방 목록 조회", description = "참여 중인 방 목록을 커서 기반 페이지네이션으로 조회합니다.")
    @GetMapping
    public RoomListResponse getMyRooms(
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "커서 (joinedAt)") @RequestParam(required = false) Instant cursor,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size
    ) {
        return RoomListResponse.from(roomService.getMyRooms(userId, cursor, size));
    }

    @Operation(summary = "방 상세 조회", description = "방 메타정보를 조회합니다. 방 멤버만 접근 가능합니다.")
    @GetMapping("/{roomId}")
    public RoomDetailResponse getDetail(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable UUID roomId
    ) {
        return RoomDetailResponse.from(roomService.getDetail(roomId, userId));
    }

    @Operation(summary = "방 수정", description = "방 제목, 여행지, 날짜를 수정합니다. HOST만 가능합니다.")
    @PatchMapping("/{roomId}")
    public RoomDetailResponse update(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable UUID roomId,
            @RequestBody @Valid UpdateRoomRequest request
    ) {
        return RoomDetailResponse.from(roomService.update(roomId, request.toCommand(), userId));
    }

    @Operation(summary = "방 삭제", description = "방을 삭제합니다 (soft delete). HOST만 가능합니다.")
    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable UUID roomId
    ) {
        roomService.delete(roomId, userId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/controller/RoomController.java
git commit -m "feat: RoomController (방 CRUD 엔드포인트) 추가"
```

---

## Task 12: RoomController 단위 테스트

**Files:**
- Create: `src/test/java/com/howaboutus/backend/rooms/controller/RoomControllerTest.java`

- [ ] **Step 1: 컨트롤러 테스트 작성**

```java
package com.howaboutus.backend.rooms.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.common.config.SecurityConfig;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.service.RoomService;
import com.howaboutus.backend.rooms.service.dto.RoomCreateCommand;
import com.howaboutus.backend.rooms.service.dto.RoomDetailResult;
import com.howaboutus.backend.rooms.service.dto.RoomListResult;
import com.howaboutus.backend.rooms.service.dto.RoomUpdateCommand;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoomController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomService roomService;

    private static final UUID ROOM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long USER_ID = 1L;
    private static final RoomDetailResult ROOM_DETAIL = new RoomDetailResult(
            ROOM_ID, "부산 여행", "부산",
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3),
            "aB3xK9mQ2w", 4, RoomRole.HOST,
            Instant.parse("2026-04-20T00:00:00Z"));

    @Test
    @DisplayName("방 생성 성공 시 201을 반환한다")
    void createRoomReturns201() throws Exception {
        given(roomService.create(any(RoomCreateCommand.class), eq(USER_ID))).willReturn(ROOM_DETAIL);

        mockMvc.perform(post("/rooms")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"부산 여행","destination":"부산","startDate":"2026-05-01","endDate":"2026-05-03"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$.title").value("부산 여행"))
                .andExpect(jsonPath("$.inviteCode").value("aB3xK9mQ2w"))
                .andExpect(jsonPath("$.role").value("HOST"));
    }

    @Test
    @DisplayName("title이 없으면 방 생성 시 400을 반환한다")
    void createRoomReturns400WhenTitleMissing() throws Exception {
        mockMvc.perform(post("/rooms")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destination":"부산"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("방 제목은 필수입니다"));

        verifyNoInteractions(roomService);
    }

    @Test
    @DisplayName("방 상세 조회 성공 시 200을 반환한다")
    void getDetailReturns200() throws Exception {
        given(roomService.getDetail(ROOM_ID, USER_ID)).willReturn(ROOM_DETAIL);

        mockMvc.perform(get("/rooms/{roomId}", ROOM_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("부산 여행"))
                .andExpect(jsonPath("$.memberCount").value(4));
    }

    @Test
    @DisplayName("방 멤버가 아니면 상세 조회 시 403을 반환한다")
    void getDetailReturns403WhenNotMember() throws Exception {
        given(roomService.getDetail(ROOM_ID, USER_ID))
                .willThrow(new CustomException(ErrorCode.NOT_ROOM_MEMBER));

        mockMvc.perform(get("/rooms/{roomId}", ROOM_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ROOM_MEMBER"));
    }

    @Test
    @DisplayName("내 방 목록 조회 성공 시 200을 반환한다")
    void getMyRoomsReturns200() throws Exception {
        RoomListResult listResult = new RoomListResult(
                List.of(new RoomListResult.RoomSummary(
                        ROOM_ID, "부산 여행", "부산",
                        LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3),
                        4, "HOST", Instant.parse("2026-04-20T00:00:00Z"))),
                null, false);
        given(roomService.getMyRooms(eq(USER_ID), eq(null), eq(20))).willReturn(listResult);

        mockMvc.perform(get("/rooms")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms[0].title").value("부산 여행"))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("방 수정 성공 시 200을 반환한다")
    void updateRoomReturns200() throws Exception {
        given(roomService.update(eq(ROOM_ID), any(RoomUpdateCommand.class), eq(USER_ID)))
                .willReturn(ROOM_DETAIL);

        mockMvc.perform(patch("/rooms/{roomId}", ROOM_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"부산 맛집 여행"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("부산 여행"));
    }

    @Test
    @DisplayName("HOST가 아니면 방 수정 시 403을 반환한다")
    void updateRoomReturns403WhenNotHost() throws Exception {
        given(roomService.update(eq(ROOM_ID), any(RoomUpdateCommand.class), eq(USER_ID)))
                .willThrow(new CustomException(ErrorCode.NOT_ROOM_HOST));

        mockMvc.perform(patch("/rooms/{roomId}", ROOM_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"변경"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ROOM_HOST"));
    }

    @Test
    @DisplayName("방 삭제 성공 시 204를 반환한다")
    void deleteRoomReturns204() throws Exception {
        mockMvc.perform(delete("/rooms/{roomId}", ROOM_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isNoContent());

        then(roomService).should().delete(ROOM_ID, USER_ID);
    }
}
```

- [ ] **Step 2: 테스트 실행**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.controller.RoomControllerTest"`
Expected: PASS (8 tests)

- [ ] **Step 3: 커밋**

```bash
git add src/test/java/com/howaboutus/backend/rooms/controller/RoomControllerTest.java
git commit -m "test: RoomController 단위 테스트 추가"
```

---

## Task 13: 전체 빌드 + 문서 업데이트

**Files:**
- Modify: `docs/ai/features.md`
- Modify: `docs/ai/erd.md`

- [ ] **Step 1: 전체 빌드 확인**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: features.md — Rooms CRUD 항목을 `[x]`로 변경**

```markdown
| `[x]` | 방 생성 | 방 제목, 여행지, 날짜 입력 → invite_code 자동 발급 | rooms |
| `[x]` | 내 방 목록 조회 | 내가 참여 중인 방 목록 | rooms, room_members |
| `[x]` | 방 상세 조회 | 방 메타정보 (제목, 여행지, 날짜, 멤버 수 등) | rooms |
| `[x]` | 방 수정 | 방 제목, 여행지, 날짜 수정 (HOST만) | rooms |
| `[x]` | 방 삭제 | 방 삭제 (HOST만, soft delete) | rooms |
```

- [ ] **Step 3: erd.md — rooms 테이블에 deleted_at 추가, room_members role 설명 갱신**

rooms 테이블에 행 추가:
```markdown
| deleted_at | TIMESTAMP | NULLABLE | Soft delete 시각 |
```

room_members 테이블의 role DEFAULT 설명을 `HOST / MEMBER / PENDING`으로 변경.

- [ ] **Step 4: 커밋**

```bash
git add docs/ai/features.md docs/ai/erd.md
git commit -m "docs: Rooms CRUD 구현 완료 반영 (features, erd 업데이트)"
```

---

## 구현 시 주의사항

1. **패키지 이름**: 기존 코드가 `rooms`(복수형) 사용 중이므로 설계 문서의 `room`(단수)이 아닌 `rooms`를 유지
2. **삼항 연산자 금지**: CONTRIBUTING.md 규칙. if/else로 대체 (Part 2의 RoomService.update에서 삼항 사용 부분 주의 — 구현 시 if문으로 변환)
3. **인증 임시 처리**: `X-User-Id` 헤더 사용. 향후 JWT 필터 연동 시 교체
4. **ROOM_NOT_FOUND 메시지 변경**: 기존 bookmarks에서도 사용하지만 메시지만 변경이라 기능 영향 없음
