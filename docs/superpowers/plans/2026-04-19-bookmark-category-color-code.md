# Bookmark Category Color Code Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add required `colorCode` support to bookmark category create/update/list flows and persist it on bookmark categories.

**Architecture:** Extend the bookmark category entity and DTO pipeline so `colorCode` is stored as a first-class category property. Validate the request format at the controller boundary, keep service normalization focused on trimming and persistence, and update docs so the API and ERD match the code.

**Tech Stack:** Spring Boot, Spring MVC validation, Spring Data JPA, JUnit 5, MockMvc

---

### Task 1: Add controller-level expectations for `colorCode`

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/bookmarks/controller/BookmarkCategoryControllerTest.java`
- Test: `src/test/java/com/howaboutus/backend/bookmarks/controller/BookmarkCategoryControllerTest.java`

- [ ] **Step 1: Write the failing tests**

```java
mockMvc.perform(post("/rooms/{roomId}/bookmark-categories", ROOM_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "맛집", "colorCode": "#FF8800"}
                        """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.colorCode").value("#FF8800"));

mockMvc.perform(patch("/rooms/{roomId}/bookmark-categories/{categoryId}", ROOM_ID, CATEGORY_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "카페", "colorCode": "FF8800"}
                        """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("colorCode는 #RRGGBB 형식이어야 합니다"));
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.howaboutus.backend.bookmarks.controller.BookmarkCategoryControllerTest`
Expected: FAIL because `colorCode` is not present in request/response handling yet.

- [ ] **Step 3: Write minimal implementation**

```java
public record CreateBookmarkCategoryRequest(String name, String colorCode) { }
public record RenameBookmarkCategoryRequest(String name, String colorCode) { }
public record BookmarkCategoryResponse(..., String colorCode, ...) { }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.howaboutus.backend.bookmarks.controller.BookmarkCategoryControllerTest`
Expected: PASS

### Task 2: Add service and entity support for `colorCode`

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/bookmarks/entity/BookmarkCategory.java`
- Modify: `src/main/java/com/howaboutus/backend/bookmarks/service/BookmarkCategoryService.java`
- Modify: `src/main/java/com/howaboutus/backend/bookmarks/service/dto/BookmarkCategoryCreateCommand.java`
- Modify: `src/main/java/com/howaboutus/backend/bookmarks/service/dto/BookmarkCategoryRenameCommand.java`
- Modify: `src/main/java/com/howaboutus/backend/bookmarks/service/dto/BookmarkCategoryResult.java`
- Modify: `src/test/java/com/howaboutus/backend/bookmarks/service/BookmarkCategoryServiceTest.java`
- Test: `src/test/java/com/howaboutus/backend/bookmarks/service/BookmarkCategoryServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
BookmarkCategoryResult result = bookmarkCategoryService.create(roomId, new BookmarkCategoryCreateCommand("맛집", "#FF8800"));

assertThat(result.colorCode()).isEqualTo("#FF8800");
assertThat(category.getColorCode()).isEqualTo("#FF8800");
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.howaboutus.backend.bookmarks.service.BookmarkCategoryServiceTest`
Expected: FAIL because the command/entity/result types do not carry `colorCode`.

- [ ] **Step 3: Write minimal implementation**

```java
@Column(name = "color_code", nullable = false, length = 7)
private String colorCode;

public void update(String name, String colorCode) {
    this.name = name;
    this.colorCode = colorCode;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.howaboutus.backend.bookmarks.service.BookmarkCategoryServiceTest`
Expected: PASS

### Task 3: Verify the HTTP integration flow and update docs

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/bookmarks/BookmarkCategoryIntegrationTest.java`
- Modify: `docs/ai/features.md`
- Modify: `docs/ai/erd.md`
- Test: `src/test/java/com/howaboutus/backend/bookmarks/BookmarkCategoryIntegrationTest.java`

- [ ] **Step 1: Write the failing integration assertions**

```java
.content("""
        {"name": "맛집", "colorCode": "#FF8800"}
        """))
.andExpect(jsonPath("$.colorCode").value("#FF8800"))

.content("""
        {"name": "카페", "colorCode": "#3366FF"}
        """))
.andExpect(jsonPath("$.colorCode").value("#3366FF"));
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.howaboutus.backend.bookmarks.BookmarkCategoryIntegrationTest`
Expected: FAIL because the persistence and HTTP response do not include `colorCode` yet.

- [ ] **Step 3: Write minimal implementation and docs**

```md
- [x] | 보관함 카테고리 목록 조회 | 방에서 사용 가능한 북마크 카테고리 목록 조회, 색상 코드 포함 | bookmark_categories |
| color_code | VARCHAR(7) | NOT NULL | 카테고리 색상 코드 (#RRGGBB) |
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.howaboutus.backend.bookmarks.BookmarkCategoryIntegrationTest`
Expected: PASS

### Task 4: Run focused regression verification

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/bookmarks/controller/dto/CreateBookmarkCategoryRequest.java`
- Modify: `src/main/java/com/howaboutus/backend/bookmarks/controller/dto/RenameBookmarkCategoryRequest.java`
- Modify: `src/main/java/com/howaboutus/backend/bookmarks/controller/dto/BookmarkCategoryResponse.java`
- Modify: `src/main/java/com/howaboutus/backend/bookmarks/controller/BookmarkCategoryController.java`
- Modify: `src/main/java/com/howaboutus/backend/bookmarks/service/BookmarkCategoryService.java`
- Modify: `src/main/java/com/howaboutus/backend/bookmarks/entity/BookmarkCategory.java`
- Modify: `src/main/java/com/howaboutus/backend/bookmarks/service/dto/BookmarkCategoryCreateCommand.java`
- Modify: `src/main/java/com/howaboutus/backend/bookmarks/service/dto/BookmarkCategoryRenameCommand.java`
- Modify: `src/main/java/com/howaboutus/backend/bookmarks/service/dto/BookmarkCategoryResult.java`
- Test: `src/test/java/com/howaboutus/backend/bookmarks/controller/BookmarkCategoryControllerTest.java`
- Test: `src/test/java/com/howaboutus/backend/bookmarks/service/BookmarkCategoryServiceTest.java`
- Test: `src/test/java/com/howaboutus/backend/bookmarks/BookmarkCategoryIntegrationTest.java`

- [ ] **Step 1: Run focused verification**

Run: `./gradlew test --tests com.howaboutus.backend.bookmarks.controller.BookmarkCategoryControllerTest --tests com.howaboutus.backend.bookmarks.service.BookmarkCategoryServiceTest --tests com.howaboutus.backend.bookmarks.BookmarkCategoryIntegrationTest`
Expected: PASS

- [ ] **Step 2: Review docs and code alignment**

Check that `colorCode` is required in create/update request handling and present in create/update/list responses, and that `docs/ai/features.md` plus `docs/ai/erd.md` describe the same rule.
