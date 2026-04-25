# 보안 설정 전환 + 기존 테스트 인증 추가 (Part 1)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `anyRequest().permitAll()`을 `anyRequest().authenticated()`로 전환하고, 이로 인해 깨지는 기존 테스트들에 JWT 쿠키 인증을 추가한다.

**Architecture:** SecurityConfig의 기본 정책을 인증 필수로 변경한 뒤, PlaceControllerTest·BookmarkControllerTest·BookmarkCategoryControllerTest에 `Cookie("access_token", "valid-jwt")` + `jwtProvider.extractUserId` mock 패턴을 적용한다.

**Tech Stack:** Spring Security, JWT (쿠키 기반), Spring Boot 4.0.5, JUnit 5, MockMvc

**연관 Plan:** Part 2 — `docs/superpowers/plans/2026-04-25-rooms-auth-part2-room-controller.md`

---

## File Structure

| 파일 | 변경 유형 | 역할 |
|------|----------|------|
| `src/main/java/com/howaboutus/backend/common/config/SecurityConfig.java` | 수정 | `anyRequest().authenticated()` 전환 |
| `src/test/java/com/howaboutus/backend/common/config/SecurityConfigTest.java` | 수정 | 검증 테스트 추가 |
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

Expected: 2개 테스트 모두 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/config/SecurityConfig.java src/test/java/com/howaboutus/backend/common/config/SecurityConfigTest.java
git commit -m "feat: anyRequest().authenticated()로 보안 설정 전환"
```

---

### Task 2: PlaceControllerTest — JWT 쿠키 인증 추가

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java`

- [ ] **Step 1: import, 상수, setUp에 JWT mock 추가**

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

- [ ] **Step 2: searchRequest 헬퍼에 쿠키 추가**

```java
private MockHttpServletRequestBuilder searchRequest(String query) {
    return get(SEARCH_PATH)
            .cookie(new Cookie("access_token", VALID_TOKEN))
            .param("query", query)
            .param("latitude", String.valueOf(DEFAULT_LAT))
            .param("longitude", String.valueOf(DEFAULT_LNG));
}
```

- [ ] **Step 3: searchRequest를 사용하지 않는 모든 mockMvc.perform에 쿠키 추가**

대상 테스트 (각 `mockMvc.perform(get(...)` 호출에 `.cookie(new Cookie("access_token", VALID_TOKEN))` 추가):

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

- [ ] **Step 4: PlaceControllerTest 실행하여 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.places.controller.PlaceControllerTest"`

Expected: 모든 테스트 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/test/java/com/howaboutus/backend/places/controller/PlaceControllerTest.java
git commit -m "test: PlaceControllerTest에 JWT 쿠키 인증 추가"
```

---

### Task 3: BookmarkControllerTest — JWT 쿠키 인증 추가

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

9개 테스트의 `mockMvc.perform(...)` 호출에 `.cookie(new Cookie("access_token", VALID_TOKEN))` 추가:

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

### Task 4: BookmarkCategoryControllerTest — JWT 쿠키 인증 추가

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

10개 테스트의 `mockMvc.perform(...)` 호출에 `.cookie(new Cookie("access_token", VALID_TOKEN))` 추가:

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
