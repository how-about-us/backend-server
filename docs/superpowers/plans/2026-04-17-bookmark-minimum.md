# Bookmark Minimum Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `Room` 최소 엔티티를 기반으로 북마크 추가, 목록 조회, 삭제 API를 구현하고 문서와 테스트를 최신 상태로 맞춘다.

**Architecture:** `rooms` 도메인에 ERD 기준 `Room` 엔티티를 추가하고, `bookmarks` 도메인에서 `Bookmark -> Room` 연관을 통해 방 소속 북마크를 관리한다. 서비스는 방 존재 여부, 방 단위 중복, 방-북마크 소속 검증을 담당하고, 컨트롤러는 방 하위 리소스 경로(`/rooms/{roomId}/bookmarks`)로 최소 CRUD를 노출한다. 인증은 아직 연결하지 않으므로 `addedBy`는 nullable로 저장한다.

**Tech Stack:** Spring Boot 4.0.5, Spring Web MVC, Spring Data JPA, PostgreSQL/Testcontainers, JUnit 5, Mockito, MockMvc

---

## File Structure

### Create

- `src/main/java/com/howaboutus/backend/rooms/entity/Room.java`
- `src/main/java/com/howaboutus/backend/rooms/repository/RoomRepository.java`
- `src/main/java/com/howaboutus/backend/bookmarks/entity/Bookmark.java`
- `src/main/java/com/howaboutus/backend/bookmarks/repository/BookmarkRepository.java`
- `src/main/java/com/howaboutus/backend/bookmarks/service/BookmarkService.java`
- `src/main/java/com/howaboutus/backend/bookmarks/service/dto/BookmarkCreateCommand.java`
- `src/main/java/com/howaboutus/backend/bookmarks/service/dto/BookmarkResult.java`
- `src/main/java/com/howaboutus/backend/bookmarks/controller/BookmarkController.java`
- `src/main/java/com/howaboutus/backend/bookmarks/controller/dto/CreateBookmarkRequest.java`
- `src/main/java/com/howaboutus/backend/bookmarks/controller/dto/BookmarkResponse.java`
- `src/test/java/com/howaboutus/backend/bookmarks/service/BookmarkServiceTest.java`
- `src/test/java/com/howaboutus/backend/bookmarks/controller/BookmarkControllerTest.java`
- `src/test/java/com/howaboutus/backend/bookmarks/BookmarkIntegrationTest.java`

### Modify

- `src/main/java/com/howaboutus/backend/common/error/ErrorCode.java`
- `src/main/java/com/howaboutus/backend/common/error/GlobalExceptionHandler.java`
- `docs/ai/features.md`
- `docs/ai/erd.md`

---

### Task 1: Room / Bookmark Persistence Model

**Files:**
- Create: `src/main/java/com/howaboutus/backend/rooms/entity/Room.java`
- Create: `src/main/java/com/howaboutus/backend/rooms/repository/RoomRepository.java`
- Create: `src/main/java/com/howaboutus/backend/bookmarks/entity/Bookmark.java`
- Create: `src/main/java/com/howaboutus/backend/bookmarks/repository/BookmarkRepository.java`
- Test: `src/test/java/com/howaboutus/backend/bookmarks/BookmarkIntegrationTest.java`

- [ ] **Step 1: 방-북마크 저장과 방 단위 중복 제약을 검증하는 실패 테스트를 작성한다**

```java
package com.howaboutus.backend.bookmarks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.howaboutus.backend.bookmarks.entity.Bookmark;
import com.howaboutus.backend.bookmarks.repository.BookmarkRepository;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.support.BaseIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BookmarkIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Test
    @DisplayName("북마크를 방에 저장하고 방 기준으로 조회한다")
    void savesBookmarkAndFindsByRoom() {
        Room room = roomRepository.save(Room.create(
                "도쿄 여행",
                "도쿄",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 3),
                "TOKYO2026",
                1L
        ));

        bookmarkRepository.save(Bookmark.create(room, "place-1", "CAFE", null));

        List<Bookmark> bookmarks = bookmarkRepository.findAllByRoom_IdOrderByCreatedAtDesc(room.getId());

        assertThat(bookmarks).hasSize(1);
        assertThat(bookmarks.get(0).getGooglePlaceId()).isEqualTo("place-1");
        assertThat(bookmarks.get(0).getRoom().getId()).isEqualTo(room.getId());
    }

    @Test
    @DisplayName("같은 방에 같은 googlePlaceId를 중복 저장하면 제약 위반이 발생한다")
    void rejectsDuplicateGooglePlaceIdInSameRoom() {
        Room room = roomRepository.save(Room.create(
                "오사카 여행",
                "오사카",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 2),
                "OSAKA2026",
                2L
        ));

        bookmarkRepository.saveAndFlush(Bookmark.create(room, "place-1", "ALL", null));

        assertThatThrownBy(() ->
                bookmarkRepository.saveAndFlush(Bookmark.create(room, "place-1", "ALL", null)))
                .isInstanceOf(Exception.class);
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "com.howaboutus.backend.bookmarks.BookmarkIntegrationTest"`
Expected: FAIL with compilation errors for missing `Room`, `Bookmark`, repository classes.

- [ ] **Step 3: Room, Bookmark 엔티티와 리포지토리를 최소 구현한다**

```java
// src/main/java/com/howaboutus/backend/rooms/entity/Room.java
package com.howaboutus.backend.rooms.entity;

import com.howaboutus.backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 200)
    private String destination;

    private LocalDate startDate;

    private LocalDate endDate;

    @Column(nullable = false, unique = true, length = 50)
    private String inviteCode;

    @Column(nullable = false)
    private Long createdBy;

    private Room(
            String title,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            String inviteCode,
            Long createdBy
    ) {
        this.title = title;
        this.destination = destination;
        this.startDate = startDate;
        this.endDate = endDate;
        this.inviteCode = inviteCode;
        this.createdBy = createdBy;
    }

    public static Room create(
            String title,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            String inviteCode,
            Long createdBy
    ) {
        return new Room(title, destination, startDate, endDate, inviteCode, createdBy);
    }
}
```

```java
// src/main/java/com/howaboutus/backend/rooms/repository/RoomRepository.java
package com.howaboutus.backend.rooms.repository;

import com.howaboutus.backend.rooms.entity.Room;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, UUID> {
}
```

```java
// src/main/java/com/howaboutus/backend/bookmarks/entity/Bookmark.java
package com.howaboutus.backend.bookmarks.entity;

import com.howaboutus.backend.common.entity.BaseTimeEntity;
import com.howaboutus.backend.rooms.entity.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "bookmarks",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"room_id", "google_place_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bookmark extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false, length = 300)
    private String googlePlaceId;

    @Column(nullable = false, length = 30)
    private String category;

    private Long addedBy;

    private Bookmark(Room room, String googlePlaceId, String category, Long addedBy) {
        this.room = room;
        this.googlePlaceId = googlePlaceId;
        this.category = category;
        this.addedBy = addedBy;
    }

    public static Bookmark create(Room room, String googlePlaceId, String category, Long addedBy) {
        String resolvedCategory = category;
        if (resolvedCategory == null || resolvedCategory.isBlank()) {
            resolvedCategory = "ALL";
        }
        return new Bookmark(room, googlePlaceId, resolvedCategory, addedBy);
    }
}
```

```java
// src/main/java/com/howaboutus/backend/bookmarks/repository/BookmarkRepository.java
package com.howaboutus.backend.bookmarks.repository;

import com.howaboutus.backend.bookmarks.entity.Bookmark;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    boolean existsByRoom_IdAndGooglePlaceId(UUID roomId, String googlePlaceId);

    List<Bookmark> findAllByRoom_IdOrderByCreatedAtDesc(UUID roomId);

    Optional<Bookmark> findByIdAndRoom_Id(Long bookmarkId, UUID roomId);
}
```

- [ ] **Step 4: 테스트를 다시 실행해 통과를 확인한다**

Run: `./gradlew test --tests "com.howaboutus.backend.bookmarks.BookmarkIntegrationTest"`
Expected: PASS

- [ ] **Step 5: 커밋한다**

```bash
git add src/main/java/com/howaboutus/backend/rooms/entity/Room.java \
  src/main/java/com/howaboutus/backend/rooms/repository/RoomRepository.java \
  src/main/java/com/howaboutus/backend/bookmarks/entity/Bookmark.java \
  src/main/java/com/howaboutus/backend/bookmarks/repository/BookmarkRepository.java \
  src/test/java/com/howaboutus/backend/bookmarks/BookmarkIntegrationTest.java
git commit -m "feat: Room과 Bookmark 영속 모델 추가"
```

### Task 2: Bookmark Service Rules

**Files:**
- Create: `src/main/java/com/howaboutus/backend/bookmarks/service/BookmarkService.java`
- Create: `src/main/java/com/howaboutus/backend/bookmarks/service/dto/BookmarkCreateCommand.java`
- Create: `src/main/java/com/howaboutus/backend/bookmarks/service/dto/BookmarkResult.java`
- Create: `src/test/java/com/howaboutus/backend/bookmarks/service/BookmarkServiceTest.java`
- Modify: `src/main/java/com/howaboutus/backend/common/error/ErrorCode.java`

- [ ] **Step 1: 서비스 규칙을 검증하는 실패 테스트를 작성한다**

```java
package com.howaboutus.backend.bookmarks.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.bookmarks.entity.Bookmark;
import com.howaboutus.backend.bookmarks.repository.BookmarkRepository;
import com.howaboutus.backend.bookmarks.service.dto.BookmarkCreateCommand;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BookmarkRepository bookmarkRepository;

    @InjectMocks
    private BookmarkService bookmarkService;

    @Test
    @DisplayName("북마크 생성 시 category가 없으면 ALL로 저장한다")
    void createsBookmarkWithDefaultCategory() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", LocalDate.now(), LocalDate.now().plusDays(1), "TOKYO", 1L);
        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkRepository.existsByRoom_IdAndGooglePlaceId(roomId, "place-1")).willReturn(false);
        given(bookmarkRepository.save(org.mockito.ArgumentMatchers.any(Bookmark.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        bookmarkService.create(roomId, new BookmarkCreateCommand("place-1", null));

        ArgumentCaptor<Bookmark> captor = ArgumentCaptor.forClass(Bookmark.class);
        verify(bookmarkRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo("ALL");
        assertThat(captor.getValue().getAddedBy()).isNull();
    }

    @Test
    @DisplayName("없는 방에 북마크를 생성하면 ROOM_NOT_FOUND 예외가 발생한다")
    void throwsWhenRoomNotFound() {
        UUID roomId = UUID.randomUUID();
        given(roomRepository.findById(roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookmarkService.create(roomId, new BookmarkCreateCommand("place-1", "CAFE")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("같은 방에 같은 googlePlaceId가 있으면 BOOKMARK_ALREADY_EXISTS 예외가 발생한다")
    void throwsWhenBookmarkAlreadyExists() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("후쿠오카 여행", "후쿠오카", LocalDate.now(), LocalDate.now().plusDays(2), "FUK", 1L);
        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkRepository.existsByRoom_IdAndGooglePlaceId(roomId, "place-1")).willReturn(true);

        assertThatThrownBy(() -> bookmarkService.create(roomId, new BookmarkCreateCommand("place-1", "ALL")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKMARK_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("없는 방의 목록을 조회하면 ROOM_NOT_FOUND 예외가 발생한다")
    void throwsWhenListingUnknownRoom() {
        UUID roomId = UUID.randomUUID();
        given(roomRepository.existsById(roomId)).willReturn(false);

        assertThatThrownBy(() -> bookmarkService.getBookmarks(roomId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 방 북마크를 삭제하려 하면 BOOKMARK_NOT_FOUND 예외가 발생한다")
    void throwsWhenDeletingBookmarkOutsideRoom() {
        UUID roomId = UUID.randomUUID();
        given(roomRepository.existsById(roomId)).willReturn(true);
        given(bookmarkRepository.findByIdAndRoom_Id(99L, roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookmarkService.delete(roomId, 99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKMARK_NOT_FOUND);
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "com.howaboutus.backend.bookmarks.service.BookmarkServiceTest"`
Expected: FAIL with compilation errors for missing service classes and `ErrorCode` entries.

- [ ] **Step 3: 서비스 DTO, 서비스 구현, 에러 코드를 추가한다**

```java
// src/main/java/com/howaboutus/backend/common/error/ErrorCode.java
// Add after existing unauthorized codes
ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다"),
BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "북마크를 찾을 수 없습니다"),
BOOKMARK_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 보관함에 추가된 장소입니다"),
```

```java
// src/main/java/com/howaboutus/backend/bookmarks/service/dto/BookmarkCreateCommand.java
package com.howaboutus.backend.bookmarks.service.dto;

public record BookmarkCreateCommand(String googlePlaceId, String category) {
}
```

```java
// src/main/java/com/howaboutus/backend/bookmarks/service/dto/BookmarkResult.java
package com.howaboutus.backend.bookmarks.service.dto;

import com.howaboutus.backend.bookmarks.entity.Bookmark;
import java.time.Instant;
import java.util.UUID;

public record BookmarkResult(
        Long bookmarkId,
        UUID roomId,
        String googlePlaceId,
        String category,
        Long addedBy,
        Instant createdAt
) {
    public static BookmarkResult from(Bookmark bookmark) {
        return new BookmarkResult(
                bookmark.getId(),
                bookmark.getRoom().getId(),
                bookmark.getGooglePlaceId(),
                bookmark.getCategory(),
                bookmark.getAddedBy(),
                bookmark.getCreatedAt()
        );
    }
}
```

```java
// src/main/java/com/howaboutus/backend/bookmarks/service/BookmarkService.java
package com.howaboutus.backend.bookmarks.service;

import com.howaboutus.backend.bookmarks.entity.Bookmark;
import com.howaboutus.backend.bookmarks.repository.BookmarkRepository;
import com.howaboutus.backend.bookmarks.service.dto.BookmarkCreateCommand;
import com.howaboutus.backend.bookmarks.service.dto.BookmarkResult;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkService {

    private final RoomRepository roomRepository;
    private final BookmarkRepository bookmarkRepository;

    @Transactional
    public BookmarkResult create(UUID roomId, BookmarkCreateCommand command) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        if (bookmarkRepository.existsByRoom_IdAndGooglePlaceId(roomId, command.googlePlaceId())) {
            throw new CustomException(ErrorCode.BOOKMARK_ALREADY_EXISTS);
        }

        Bookmark bookmark = Bookmark.create(room, command.googlePlaceId(), command.category(), null);
        return BookmarkResult.from(bookmarkRepository.save(bookmark));
    }

    public List<BookmarkResult> getBookmarks(UUID roomId) {
        validateRoomExists(roomId);
        return bookmarkRepository.findAllByRoom_IdOrderByCreatedAtDesc(roomId).stream()
                .map(BookmarkResult::from)
                .toList();
    }

    @Transactional
    public void delete(UUID roomId, Long bookmarkId) {
        validateRoomExists(roomId);
        Bookmark bookmark = bookmarkRepository.findByIdAndRoom_Id(bookmarkId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOKMARK_NOT_FOUND));
        bookmarkRepository.delete(bookmark);
    }

    private void validateRoomExists(UUID roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new CustomException(ErrorCode.ROOM_NOT_FOUND);
        }
    }
}
```

- [ ] **Step 4: 테스트를 다시 실행해 통과를 확인한다**

Run: `./gradlew test --tests "com.howaboutus.backend.bookmarks.service.BookmarkServiceTest"`
Expected: PASS

- [ ] **Step 5: 커밋한다**

```bash
git add src/main/java/com/howaboutus/backend/common/error/ErrorCode.java \
  src/main/java/com/howaboutus/backend/bookmarks/service/BookmarkService.java \
  src/main/java/com/howaboutus/backend/bookmarks/service/dto/BookmarkCreateCommand.java \
  src/main/java/com/howaboutus/backend/bookmarks/service/dto/BookmarkResult.java \
  src/test/java/com/howaboutus/backend/bookmarks/service/BookmarkServiceTest.java
git commit -m "feat: 북마크 서비스 규칙 구현"
```

### Task 3: Bookmark Controller and Request Validation

**Files:**
- Create: `src/main/java/com/howaboutus/backend/bookmarks/controller/BookmarkController.java`
- Create: `src/main/java/com/howaboutus/backend/bookmarks/controller/dto/CreateBookmarkRequest.java`
- Create: `src/main/java/com/howaboutus/backend/bookmarks/controller/dto/BookmarkResponse.java`
- Create: `src/test/java/com/howaboutus/backend/bookmarks/controller/BookmarkControllerTest.java`
- Modify: `src/main/java/com/howaboutus/backend/common/error/GlobalExceptionHandler.java`

- [ ] **Step 1: 요청 본문 검증과 상태 코드를 검증하는 실패 테스트를 작성한다**

```java
package com.howaboutus.backend.bookmarks.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.bookmarks.service.BookmarkService;
import com.howaboutus.backend.bookmarks.service.dto.BookmarkResult;
import com.howaboutus.backend.common.config.SecurityConfig;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import java.time.Instant;
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

@WebMvcTest(BookmarkController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class BookmarkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookmarkService bookmarkService;

    @Test
    @DisplayName("googlePlaceId가 공백이면 400을 반환한다")
    void returnsBadRequestWhenGooglePlaceIdIsBlank() throws Exception {
        UUID roomId = UUID.randomUUID();

        mockMvc.perform(post("/rooms/{roomId}/bookmarks", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"googlePlaceId":"   ","category":"CAFE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("googlePlaceId는 공백일 수 없습니다"));

        verifyNoInteractions(bookmarkService);
    }

    @Test
    @DisplayName("북마크 생성 성공 시 201과 응답 바디를 반환한다")
    void createsBookmark() throws Exception {
        UUID roomId = UUID.randomUUID();
        given(bookmarkService.create(
                org.mockito.ArgumentMatchers.eq(roomId),
                org.mockito.ArgumentMatchers.any()))
                .willReturn(new BookmarkResult(1L, roomId, "place-1", "CAFE", null, Instant.parse("2026-04-17T00:00:00Z")));

        mockMvc.perform(post("/rooms/{roomId}/bookmarks", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"googlePlaceId":"place-1","category":"CAFE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookmarkId").value(1L))
                .andExpect(jsonPath("$.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.googlePlaceId").value("place-1"));
    }

    @Test
    @DisplayName("없는 방 조회 요청 시 404를 반환한다")
    void returns404WhenRoomNotFound() throws Exception {
        UUID roomId = UUID.randomUUID();
        given(bookmarkService.getBookmarks(roomId))
                .willThrow(new CustomException(ErrorCode.ROOM_NOT_FOUND));

        mockMvc.perform(get("/rooms/{roomId}/bookmarks", roomId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("삭제 성공 시 204를 반환한다")
    void deletesBookmark() throws Exception {
        UUID roomId = UUID.randomUUID();

        mockMvc.perform(delete("/rooms/{roomId}/bookmarks/{bookmarkId}", roomId, 1L))
                .andExpect(status().isNoContent());

        then(bookmarkService).should().delete(roomId, 1L);
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "com.howaboutus.backend.bookmarks.controller.BookmarkControllerTest"`
Expected: FAIL because `BookmarkController`, request/response DTOs, and request-body validation handler do not exist yet.

- [ ] **Step 3: 컨트롤러, DTO, `MethodArgumentNotValidException` 핸들러를 구현한다**

```java
// src/main/java/com/howaboutus/backend/bookmarks/controller/dto/CreateBookmarkRequest.java
package com.howaboutus.backend.bookmarks.controller.dto;

import com.howaboutus.backend.bookmarks.service.dto.BookmarkCreateCommand;
import jakarta.validation.constraints.NotBlank;

public record CreateBookmarkRequest(
        @NotBlank(message = "googlePlaceId는 공백일 수 없습니다")
        String googlePlaceId,
        String category
) {
    public BookmarkCreateCommand toCommand() {
        return new BookmarkCreateCommand(googlePlaceId, category);
    }
}
```

```java
// src/main/java/com/howaboutus/backend/bookmarks/controller/dto/BookmarkResponse.java
package com.howaboutus.backend.bookmarks.controller.dto;

import com.howaboutus.backend.bookmarks.service.dto.BookmarkResult;
import java.time.Instant;
import java.util.UUID;

public record BookmarkResponse(
        Long bookmarkId,
        UUID roomId,
        String googlePlaceId,
        String category,
        Long addedBy,
        Instant createdAt
) {
    public static BookmarkResponse from(BookmarkResult result) {
        return new BookmarkResponse(
                result.bookmarkId(),
                result.roomId(),
                result.googlePlaceId(),
                result.category(),
                result.addedBy(),
                result.createdAt()
        );
    }
}
```

```java
// src/main/java/com/howaboutus/backend/bookmarks/controller/BookmarkController.java
package com.howaboutus.backend.bookmarks.controller;

import com.howaboutus.backend.bookmarks.controller.dto.BookmarkResponse;
import com.howaboutus.backend.bookmarks.controller.dto.CreateBookmarkRequest;
import com.howaboutus.backend.bookmarks.service.BookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Bookmarks", description = "방 보관함 API")
@RestController
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @Operation(summary = "보관함에 장소 추가")
    @PostMapping("/rooms/{roomId}/bookmarks")
    @ResponseStatus(HttpStatus.CREATED)
    public BookmarkResponse create(
            @PathVariable UUID roomId,
            @Valid @RequestBody CreateBookmarkRequest request
    ) {
        return BookmarkResponse.from(bookmarkService.create(roomId, request.toCommand()));
    }

    @Operation(summary = "보관함 목록 조회")
    @GetMapping("/rooms/{roomId}/bookmarks")
    public List<BookmarkResponse> getBookmarks(@PathVariable UUID roomId) {
        return bookmarkService.getBookmarks(roomId).stream()
                .map(BookmarkResponse::from)
                .toList();
    }

    @Operation(summary = "보관함 항목 삭제")
    @DeleteMapping("/rooms/{roomId}/bookmarks/{bookmarkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID roomId, @PathVariable Long bookmarkId) {
        bookmarkService.delete(roomId, bookmarkId);
    }
}
```

```java
// src/main/java/com/howaboutus/backend/common/error/GlobalExceptionHandler.java
// Add import: org.springframework.web.bind.MethodArgumentNotValidException;

@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
    String message = e.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> fieldError.getDefaultMessage())
            .findFirst()
            .orElse("요청 바디가 유효하지 않습니다");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST, message));
}
```

- [ ] **Step 4: 테스트를 다시 실행해 통과를 확인한다**

Run: `./gradlew test --tests "com.howaboutus.backend.bookmarks.controller.BookmarkControllerTest"`
Expected: PASS

- [ ] **Step 5: 커밋한다**

```bash
git add src/main/java/com/howaboutus/backend/bookmarks/controller/BookmarkController.java \
  src/main/java/com/howaboutus/backend/bookmarks/controller/dto/CreateBookmarkRequest.java \
  src/main/java/com/howaboutus/backend/bookmarks/controller/dto/BookmarkResponse.java \
  src/main/java/com/howaboutus/backend/common/error/GlobalExceptionHandler.java \
  src/test/java/com/howaboutus/backend/bookmarks/controller/BookmarkControllerTest.java
git commit -m "feat: 북마크 API 추가"
```

### Task 4: End-to-End Integration and Documentation

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/bookmarks/BookmarkIntegrationTest.java`
- Modify: `docs/ai/features.md`
- Modify: `docs/ai/erd.md`

- [ ] **Step 1: 실제 HTTP 경로 기준 통합 테스트를 추가해 실패를 확인한다**

```java
package com.howaboutus.backend.bookmarks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.bookmarks.repository.BookmarkRepository;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.support.BaseIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class BookmarkIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Test
    @DisplayName("북마크 추가 후 목록 조회와 삭제까지 수행할 수 있다")
    void createsListsAndDeletesBookmark() throws Exception {
        Room room = roomRepository.save(Room.create(
                "삿포로 여행",
                "삿포로",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3),
                "SAP2026",
                1L
        ));

        mockMvc.perform(post("/rooms/{roomId}/bookmarks", room.getId())
                        .contentType("application/json")
                        .content("""
                                {"googlePlaceId":"place-1","category":"CAFE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.googlePlaceId").value("place-1"));

        mockMvc.perform(get("/rooms/{roomId}/bookmarks", room.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].googlePlaceId").value("place-1"));

        Long bookmarkId = bookmarkRepository.findAll().get(0).getId();

        mockMvc.perform(delete("/rooms/{roomId}/bookmarks/{bookmarkId}", room.getId(), bookmarkId))
                .andExpect(status().isNoContent());

        assertThat(bookmarkRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("없는 방에 북마크를 추가하면 404를 반환한다")
    void returns404WhenCreatingBookmarkInUnknownRoom() throws Exception {
        mockMvc.perform(post("/rooms/{roomId}/bookmarks", java.util.UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {"googlePlaceId":"place-1","category":"ALL"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("같은 방에 같은 장소를 두 번 추가하면 409를 반환한다")
    void returns409WhenDuplicateBookmarkRequested() throws Exception {
        Room room = roomRepository.save(Room.create(
                "나고야 여행",
                "나고야",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 2),
                "NGY2026",
                3L
        ));

        mockMvc.perform(post("/rooms/{roomId}/bookmarks", room.getId())
                        .contentType("application/json")
                        .content("""
                                {"googlePlaceId":"place-1","category":"ALL"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/rooms/{roomId}/bookmarks", room.getId())
                        .contentType("application/json")
                        .content("""
                                {"googlePlaceId":"place-1","category":"ALL"}
                                """))
                .andExpect(status().isConflict());
    }
}
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew test --tests "com.howaboutus.backend.bookmarks.BookmarkIntegrationTest"`
Expected: FAIL until controller, service, and mappings are all wired together.

- [ ] **Step 3: 통합 테스트가 통과하도록 문서와 세부 구현을 정리한다**

```md
<!-- docs/ai/features.md -->
| `[x]` | 보관함에 장소 추가 | 검색된 장소를 방의 후보지로 등록 | bookmarks |
| `[x]` | 보관함 목록 조회 | 방의 후보지 목록 (카테고리 필터) | bookmarks |
| `[x]` | 보관함 항목 삭제 | 후보지에서 제거 | bookmarks |
```

```md
<!-- docs/ai/erd.md -->
| added_by | BIGINT | NULLABLE, FK → users.id | 등록한 사용자 (인증 연동 전까지 nullable) |
```

```java
public List<BookmarkResult> getBookmarks(UUID roomId) {
    validateRoomExists(roomId);
    return bookmarkRepository.findAllByRoom_IdOrderByCreatedAtDesc(roomId).stream()
            .map(BookmarkResult::from)
            .toList();
}
```

- [ ] **Step 4: 관련 테스트와 전체 테스트를 실행해 모두 통과하는지 확인한다**

Run: `./gradlew test --tests "com.howaboutus.backend.bookmarks.service.BookmarkServiceTest" --tests "com.howaboutus.backend.bookmarks.controller.BookmarkControllerTest" --tests "com.howaboutus.backend.bookmarks.BookmarkIntegrationTest"`
Expected: PASS

Run: `./gradlew test`
Expected: PASS

- [ ] **Step 5: 커밋한다**

```bash
git add src/test/java/com/howaboutus/backend/bookmarks/BookmarkIntegrationTest.java \
  docs/ai/features.md \
  docs/ai/erd.md
git commit -m "feat: 북마크 최소 기능 마무리"
```
