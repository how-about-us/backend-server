# Rooms 인증 전환 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `anyRequest().permitAll()`을 `anyRequest().authenticated()`로 전환하고, RoomController의 `X-User-Id` 헤더 방식을 `@AuthenticationPrincipal`로 교체한다.

**Architecture:** SecurityConfig에서 화이트리스트 외 모든 요청에 인증을 요구하도록 전환한다. RoomController는 JWT 쿠키에서 추출된 principal을 사용하도록 변경한다. UserControllerTest에서 이미 사용 중인 `Cookie("access_token", "valid-jwt")` + `jwtProvider.extractUserId` mock 패턴을 다른 테스트에도 동일하게 적용한다.

**Tech Stack:** Spring Security, JWT (쿠키 기반), Spring Boot 4.0.5, JUnit 5, MockMvc

---

## File Structure

| 파일 | 변경 유형 | 역할 |
|------|----------|------|
| `src/main/java/com/howaboutus/backend/common/config/SecurityConfig.java` | 수정 | `anyRequest().authenticated()` 전환 |
| `src/main/java/com/howaboutus/backend/rooms/controller/RoomController.java` | 수정 | `@AuthenticationPrincipal` 교체 |
| `src/test/java/com/howaboutus/backend/common/config/SecurityConfigTest.java` | 수정 | `anyRequest().authenticated()` 검증 테스트 추가 |
| `src/test/java/com/howaboutus/backend/rooms/controller/RoomControllerTest.java` | 수정 | JWT 쿠키 인증 방식으로 전환 |
| `src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java` | 수정 | JWT 쿠키 인증 추가 |
| `src/test/java/com/howaboutus/backend/bookmarks/controller/BookmarkControllerTest.java` | 수정 | JWT 쿠키 인증 추가 |
| `src/test/java/com/howaboutus/backend/bookmarks/controller/BookmarkCategoryControllerTest.java` | 수정 | JWT 쿠키 인증 추가 |

---

### Task 1: SecurityConfig — anyRequest().authenticated() 전환

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/common/config/SecurityConfig.java:38-49`
- Modify: `src/test/java/com/howaboutus/backend/common/config/SecurityConfigTest.java`

- [ ] **Step 1: SecurityConfigTest에 인증 없는 임의 경로 401 테스트 추가**

`src/test/java/com/howaboutus/backend/common/config/SecurityConfigTest.java`에 테스트를 추가한다:

```java
@Test
@DisplayName("인증 없이 허용 목록 외 경로 접근 시 401을 반환한다")
void returns401ForUnauthenticatedArbitraryPath() throws Exception {
    mockMvc.perform(get("/some/protected/path"))
            .andExpect(status().isUnauthorized());
}
```

- [ ] **Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.common.config.SecurityConfigTest.returns401ForUnauthenticatedArbitraryPath"`

Expected: FAIL — 현재 `anyRequest().permitAll()`이므로 401이 아닌 404를 반환한다.

- [ ] **Step 3: SecurityConfig 수정**

`src/main/java/com/howaboutus/backend/common/config/SecurityConfig.java`의 `authorizeHttpRequests` 블록을 다음으로 교체한다:

```java
.authorizeHttpRequests(authorize -> authorize
        .requestMatchers(
                "/v3/api-docs/**",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/auth/google/login",
                "/auth/refresh",
                "/auth/logout")
        .permitAll()
        .anyRequest().authenticated())
```

변경 포인트:
- `.requestMatchers("/users/me").authenticated()` 줄 제거 (anyRequest에 포함됨)
- `// TODO: API가 갖춰지면 ...` 주석 제거
- `.anyRequest().permitAll()` → `.anyRequest().authenticated()`

- [ ] **Step 4: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.common.config.SecurityConfigTest"`

Expected: 2개 테스트 모두 PASS (`returns401ForUnauthenticatedUsersMe`, `returns401ForUnauthenticatedArbitraryPath`)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/config/SecurityConfig.java src/test/java/com/howaboutus/backend/common/config/SecurityConfigTest.java
git commit -m "feat: anyRequest().authenticated()로 보안 설정 전환"
```

---

### Task 2: RoomController — @AuthenticationPrincipal로 교체

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

### Task 3: RoomControllerTest — JWT 쿠키 인증 방식으로 전환

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

### Task 4: PlaceControllerTest — JWT 쿠키 인증 추가

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java`

- [ ] **Step 1: setUp에 JWT mock 추가, searchRequest에 쿠키 추가**

import 추가:
```java
import jakarta.servlet.http.Cookie;
```

상수 추가 (기존 상수 선언 아래):
```java
private static final Long USER_ID = 1L;
private static final String VALID_TOKEN = "valid-jwt";
```

기존 `@BeforeEach setUp()` 메서드의 첫 줄에 JWT mock 추가:
```java
@BeforeEach
void setUp() {
    given(jwtProvider.extractUserId(VALID_TOKEN)).willReturn(USER_ID);
    placeSearchResult = new PlaceSearchResult(
            // ... 기존 코드 그대로 유지
    );
    // ... 나머지 기존 코드 그대로 유지
}
```

`searchRequest` 헬퍼 메서드에 쿠키 추가:
```java
private MockHttpServletRequestBuilder searchRequest(String query) {
    return get(SEARCH_PATH)
            .cookie(new Cookie("access_token", VALID_TOKEN))
            .param("query", query)
            .param("latitude", String.valueOf(DEFAULT_LAT))
            .param("longitude", String.valueOf(DEFAULT_LNG));
}
```

**`searchRequest`를 사용하지 않는 나머지 `mockMvc.perform` 호출에도 `.cookie(new Cookie("access_token", VALID_TOKEN))` 추가.** 해당 테스트 목록:

`returnsBadRequestWhenLatitudeIsOutOfRange`:
```java
mockMvc.perform(get(SEARCH_PATH)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .param("query", VALID_QUERY)
                .param("latitude", "200")
                .param("longitude", "127.0"))
```

`returnsBadRequestWhenLongitudeIsOutOfRange`:
```java
mockMvc.perform(get(SEARCH_PATH)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .param("query", VALID_QUERY)
                .param("latitude", "37.5")
                .param("longitude", "-200"))
```

`returnsBadRequestWhenRadiusIsNegative`:
```java
mockMvc.perform(get(SEARCH_PATH)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .param("query", VALID_QUERY)
                .param("latitude", "37.5")
                .param("longitude", "127.0")
                .param("radius", "-1"))
```

`returnsBadRequestWhenRadiusExceedsMaximum`:
```java
mockMvc.perform(get(SEARCH_PATH)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .param("query", VALID_QUERY)
                .param("latitude", "37.5")
                .param("longitude", "127.0")
                .param("radius", "999999"))
```

`returnsBadRequestWhenQueryParameterIsMissing`:
```java
mockMvc.perform(get(SEARCH_PATH)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .param("latitude", "37.5")
                .param("longitude", "127.0"))
```

`returnsBadRequestWhenLatitudeParameterIsMissing`:
```java
mockMvc.perform(get(SEARCH_PATH)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .param("query", VALID_QUERY)
                .param("longitude", "127.0"))
```

`returnsBadRequestWhenLongitudeParameterIsMissing`:
```java
mockMvc.perform(get(SEARCH_PATH)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .param("query", VALID_QUERY)
                .param("latitude", "37.5"))
```

`callsServiceWithLocationWhenAllLocationParamsProvided`:
```java
mockMvc.perform(get(SEARCH_PATH)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .param("query", VALID_QUERY)
                .param("latitude", "37.5")
                .param("longitude", "127.0")
                .param("radius", "3000.0"))
```

`callsServiceWithDefaultRadiusWhenRadiusNotProvided`:
```java
mockMvc.perform(get(SEARCH_PATH)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .param("query", VALID_QUERY)
                .param("latitude", "37.5")
                .param("longitude", "127.0"))
```

`returnsPlaceDetailWhenGooglePlaceIdIsValid`:
```java
mockMvc.perform(get("/places/{googlePlaceId}", "ChIJ123")
                .cookie(new Cookie("access_token", VALID_TOKEN)))
```

`returnsBadGatewayWhenPlaceDetailLookupFails`:
```java
mockMvc.perform(get("/places/{googlePlaceId}", "ChIJ123")
                .cookie(new Cookie("access_token", VALID_TOKEN)))
```

`returnsPhotoUrlForValidName`:
```java
mockMvc.perform(get("/places/photos")
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .param("photoName", "places/ChIJ123/photos/abc"))
```

`returnsBadRequestWhenNameIsBlank`:
```java
mockMvc.perform(get("/places/photos")
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .param("photoName", "   "))
```

`returnsBadRequestWhenNameParameterIsMissing`:
```java
mockMvc.perform(get("/places/photos")
                .cookie(new Cookie("access_token", VALID_TOKEN)))
```

`returnsBadGatewayWhenPhotoUrlLookupFails`:
```java
mockMvc.perform(get("/places/photos")
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .param("photoName", "places/ChIJ123/photos/abc"))
```

`returnsBadRequestWhenPhotoNameFormatIsInvalid`:
```java
mockMvc.perform(get("/places/photos")
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .param("photoName", "../../admin"))
```

- [ ] **Step 2: PlaceControllerTest 실행하여 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.places.controller.PlaceControllerTest"`

Expected: 모든 테스트 PASS

- [ ] **Step 3: 커밋**

```bash
git add src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java
git commit -m "test: PlaceControllerTest에 JWT 쿠키 인증 추가"
```

---

### Task 5: BookmarkControllerTest — JWT 쿠키 인증 추가

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/bookmarks/controller/BookmarkControllerTest.java`

- [ ] **Step 1: import, 상수, BeforeEach 추가**

import 추가:
```java
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
```

상수 추가 (기존 상수 선언 영역에):
```java
private static final Long USER_ID = 1L;
private static final String VALID_TOKEN = "valid-jwt";
```

`@BeforeEach` 추가:
```java
@BeforeEach
void setUp() {
    given(jwtProvider.extractUserId(VALID_TOKEN)).willReturn(USER_ID);
}
```

- [ ] **Step 2: 모든 mockMvc.perform에 쿠키 추가**

모든 `mockMvc.perform(...)` 호출에 `.cookie(new Cookie("access_token", VALID_TOKEN))` 추가. 9개 테스트 대상:

`returnsBadRequestWhenGooglePlaceIdIsBlank`:
```java
mockMvc.perform(post("/rooms/{roomId}/bookmarks", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"googlePlaceId": "   ", "categoryId": 10}
                        """))
```

`returnsBadRequestWhenGooglePlaceIdIsTooLong`:
```java
mockMvc.perform(post("/rooms/{roomId}/bookmarks", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"googlePlaceId": "%s", "categoryId": 10}
                        """.formatted("a".repeat(301))))
```

`returnsBadRequestWhenCategoryIdIsMissingOnCreate`:
```java
mockMvc.perform(post("/rooms/{roomId}/bookmarks", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"googlePlaceId": "place-1"}
                        """))
```

`createsBookmarkSuccessfully`:
```java
mockMvc.perform(post("/rooms/{roomId}/bookmarks", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"googlePlaceId": "place-1", "categoryId": 10}
                        """))
```

`returnsBadRequestWhenCategoryIdIsMissingOnUpdate`:
```java
mockMvc.perform(patch("/rooms/{roomId}/bookmarks/{bookmarkId}/category", ROOM_ID, BOOKMARK_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {}
                        """))
```

`updatesBookmarkCategorySuccessfully`:
```java
mockMvc.perform(patch("/rooms/{roomId}/bookmarks/{bookmarkId}/category", ROOM_ID, BOOKMARK_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"categoryId": 20}
                        """))
```

`returnsNotFoundWhenRoomIsMissing`:
```java
mockMvc.perform(get("/rooms/{roomId}/bookmarks", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .param("categoryId", "1"))
```

`returnsBookmarkListSuccessfully`:
```java
mockMvc.perform(get("/rooms/{roomId}/bookmarks", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .param("categoryId", "1"))
```

`deletesBookmarkSuccessfully`:
```java
mockMvc.perform(delete("/rooms/{roomId}/bookmarks/{bookmarkId}", ROOM_ID, BOOKMARK_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN)))
```

- [ ] **Step 3: BookmarkControllerTest 실행하여 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.bookmarks.controller.BookmarkControllerTest"`

Expected: 모든 테스트 PASS

- [ ] **Step 4: 커밋**

```bash
git add src/test/java/com/howaboutus/backend/bookmarks/controller/BookmarkControllerTest.java
git commit -m "test: BookmarkControllerTest에 JWT 쿠키 인증 추가"
```

---

### Task 6: BookmarkCategoryControllerTest — JWT 쿠키 인증 추가

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/bookmarks/controller/BookmarkCategoryControllerTest.java`

- [ ] **Step 1: import, 상수, BeforeEach 추가**

import 추가:
```java
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
```

상수 추가:
```java
private static final Long USER_ID = 1L;
private static final String VALID_TOKEN = "valid-jwt";
```

`@BeforeEach` 추가:
```java
@BeforeEach
void setUp() {
    given(jwtProvider.extractUserId(VALID_TOKEN)).willReturn(USER_ID);
}
```

- [ ] **Step 2: 모든 mockMvc.perform에 쿠키 추가**

10개 테스트 대상:

`createsBookmarkCategorySuccessfully`:
```java
mockMvc.perform(post("/rooms/{roomId}/bookmark-categories", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "맛집", "colorCode": "#FF8800"}
                        """))
```

`returnsBadRequestWhenCreateNameIsBlank`:
```java
mockMvc.perform(post("/rooms/{roomId}/bookmark-categories", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "   ", "colorCode": "#FF8800"}
                        """))
```

`returnsBadRequestWhenCreateNameIsTooLong`:
```java
mockMvc.perform(post("/rooms/{roomId}/bookmark-categories", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "colorCode": "#FF8800"}
                        """.formatted("a".repeat(51))))
```

`returnsBadRequestWhenCreateColorCodeIsInvalid`:
```java
mockMvc.perform(post("/rooms/{roomId}/bookmark-categories", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "맛집", "colorCode": "FF8800"}
                        """))
```

`returnsBookmarkCategoryListSuccessfully`:
```java
mockMvc.perform(get("/rooms/{roomId}/bookmark-categories", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN)))
```

`renamesBookmarkCategorySuccessfully`:
```java
mockMvc.perform(patch("/rooms/{roomId}/bookmark-categories/{categoryId}", ROOM_ID, CATEGORY_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "카페", "colorCode": "#3366FF"}
                        """))
```

`returnsBadRequestWhenRenameNameIsBlank`:
```java
mockMvc.perform(patch("/rooms/{roomId}/bookmark-categories/{categoryId}", ROOM_ID, CATEGORY_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "   ", "colorCode": "#3366FF"}
                        """))
```

`returnsBadRequestWhenRenameColorCodeIsInvalid`:
```java
mockMvc.perform(patch("/rooms/{roomId}/bookmark-categories/{categoryId}", ROOM_ID, CATEGORY_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "카페", "colorCode": "3366FF"}
                        """))
```

`deletesBookmarkCategorySuccessfully`:
```java
mockMvc.perform(delete("/rooms/{roomId}/bookmark-categories/{categoryId}", ROOM_ID, CATEGORY_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN)))
```

`returnsNotFoundWhenServiceThrowsRoomNotFound`:
```java
mockMvc.perform(post("/rooms/{roomId}/bookmark-categories", ROOM_ID)
                .cookie(new Cookie("access_token", VALID_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "맛집", "colorCode": "#FF8800"}
                        """))
```

- [ ] **Step 3: BookmarkCategoryControllerTest 실행하여 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.bookmarks.controller.BookmarkCategoryControllerTest"`

Expected: 모든 테스트 PASS

- [ ] **Step 4: 커밋**

```bash
git add src/test/java/com/howaboutus/backend/bookmarks/controller/BookmarkCategoryControllerTest.java
git commit -m "test: BookmarkCategoryControllerTest에 JWT 쿠키 인증 추가"
```

---

### Task 7: 전체 테스트 통과 확인

- [ ] **Step 1: 전체 테스트 실행**

Run: `./gradlew test`

Expected: BUILD SUCCESSFUL — 모든 테스트 통과

- [ ] **Step 2: (실패 시) 실패한 테스트 분석 및 수정**

예상하지 못한 테스트 실패가 있으면 동일한 패턴(JWT 쿠키 추가)으로 수정한다.
