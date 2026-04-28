# Room 삭제 @OnDelete(CASCADE) 전환 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Room 삭제 시 서비스 레이어의 벌크 DELETE를 DB 레벨 `ON DELETE CASCADE`로 전환하여 의존도를 낮추고 삭제 로직을 단순화한다.

**Architecture:** 각 하위 엔티티의 `@ManyToOne` FK에 `@OnDelete(action = OnDeleteAction.CASCADE)`를 추가하고, `RoomService.delete()`를 `roomRepository.delete(room)` 한 줄로 축소한다. DB 마이그레이션으로 기존 FK에 `ON DELETE CASCADE`를 추가한다.

**Tech Stack:** Spring Boot 4.0.5, Hibernate 7, PostgreSQL 17, Java 21

**Spec:** `docs/superpowers/specs/2026-04-28-room-ondelete-cascade-design.md`

---

### Task 1: 엔티티에 @OnDelete(CASCADE) 추가

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/entity/RoomMember.java:33-35`
- Modify: `src/main/java/com/howaboutus/backend/schedules/entity/Schedule.java:37-39`
- Modify: `src/main/java/com/howaboutus/backend/schedules/entity/ScheduleItem.java:35-37`
- Modify: `src/main/java/com/howaboutus/backend/bookmarks/entity/BookmarkCategory.java:29-31`
- Modify: `src/main/java/com/howaboutus/backend/bookmarks/entity/Bookmark.java:28-30,38-43`

- [ ] **Step 1: RoomMember 엔티티에 @OnDelete 추가**

`src/main/java/com/howaboutus/backend/rooms/entity/RoomMember.java`의 `room` 필드(33-35행)를 수정:

```java
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
```

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Room room;
```

- [ ] **Step 2: Schedule 엔티티에 @OnDelete 추가**

`src/main/java/com/howaboutus/backend/schedules/entity/Schedule.java`의 `room` 필드(37-39행)를 수정:

```java
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
```

```java
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Room room;
```

- [ ] **Step 3: ScheduleItem 엔티티에 @OnDelete 추가**

`src/main/java/com/howaboutus/backend/schedules/entity/ScheduleItem.java`의 `schedule` 필드(35-37행)를 수정:

```java
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
```

```java
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Schedule schedule;
```

- [ ] **Step 4: BookmarkCategory 엔티티에 @OnDelete 추가**

`src/main/java/com/howaboutus/backend/bookmarks/entity/BookmarkCategory.java`의 `room` 필드(29-31행)를 수정:

```java
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
```

```java
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Room room;
```

- [ ] **Step 5: Bookmark 엔티티에 @OnDelete 추가 (2곳)**

`src/main/java/com/howaboutus/backend/bookmarks/entity/Bookmark.java`의 `room` 필드(28-30행)와 `category` 필드(38-43행)를 수정:

```java
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
```

```java
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Room room;
```

```java
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "category_id", referencedColumnName = "id", nullable = false, insertable = false, updatable = false),
            @JoinColumn(name = "room_id", referencedColumnName = "room_id", nullable = false, insertable = false, updatable = false)
    })
    @OnDelete(action = OnDeleteAction.CASCADE)
    private BookmarkCategory category;
```

- [ ] **Step 6: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/entity/RoomMember.java \
        src/main/java/com/howaboutus/backend/schedules/entity/Schedule.java \
        src/main/java/com/howaboutus/backend/schedules/entity/ScheduleItem.java \
        src/main/java/com/howaboutus/backend/bookmarks/entity/BookmarkCategory.java \
        src/main/java/com/howaboutus/backend/bookmarks/entity/Bookmark.java
git commit -m "feat: 하위 엔티티 FK에 @OnDelete(CASCADE) 추가"
```

---

### Task 2: RoomService 삭제 로직 단순화

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/service/RoomService.java:1-97`

- [ ] **Step 1: RoomService에서 불필요한 의존성과 삭제 로직 제거**

`src/main/java/com/howaboutus/backend/rooms/service/RoomService.java`에서 다음을 변경:

import 제거 (17-20행):
```java
// 아래 4개 import 삭제
import com.howaboutus.backend.bookmarks.repository.BookmarkCategoryRepository;
import com.howaboutus.backend.bookmarks.repository.BookmarkRepository;
import com.howaboutus.backend.schedules.repository.ScheduleItemRepository;
import com.howaboutus.backend.schedules.repository.ScheduleRepository;
```

필드 제거 (42-45행):
```java
// 아래 4개 필드 삭제
private final ScheduleItemRepository scheduleItemRepository;
private final ScheduleRepository scheduleRepository;
private final BookmarkRepository bookmarkRepository;
private final BookmarkCategoryRepository bookmarkCategoryRepository;
```

`delete()` 메서드 (84-97행)를 다음으로 교체:
```java
    //방 삭제 (hard delete)
    //방장만 가능하며, DB ON DELETE CASCADE로 하위 데이터가 자동 삭제된다.
    @Transactional
    public void delete(UUID roomId, Long userId) {
        Room room = getActiveRoom(roomId);
        roomAuthorizationService.requireHost(roomId, userId);
        roomRepository.delete(room);
    }
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/service/RoomService.java
git commit -m "refactor: RoomService.delete()를 DB CASCADE 위임으로 단순화"
```

---

### Task 3: Repository 벌크 DELETE 메서드 제거

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/schedules/repository/ScheduleItemRepository.java:23-25`
- Modify: `src/main/java/com/howaboutus/backend/schedules/repository/ScheduleRepository.java:35-37`
- Modify: `src/main/java/com/howaboutus/backend/bookmarks/repository/BookmarkRepository.java:28-30`
- Modify: `src/main/java/com/howaboutus/backend/bookmarks/repository/BookmarkCategoryRepository.java:22-24`
- Modify: `src/main/java/com/howaboutus/backend/rooms/repository/RoomMemberRepository.java:35-40`

- [ ] **Step 1: ScheduleItemRepository에서 deleteAllByRoomId 제거**

`src/main/java/com/howaboutus/backend/schedules/repository/ScheduleItemRepository.java`에서 23-25행 삭제:
```java
// 삭제할 코드:
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ScheduleItem si WHERE si.schedule.id IN (SELECT s.id FROM Schedule s WHERE s.room.id = :roomId)")
    void deleteAllByRoomId(@Param("roomId") UUID roomId);
```

사용하지 않는 import도 제거:
```java
// UUID import는 다른 곳에서 사용하지 않으므로 제거
import java.util.UUID;
```

`@Modifying`, `@Query`, `@Param` import는 다른 메서드에서 사용하지 않으므로 함께 제거:
```java
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
```

최종 파일:
```java
package com.howaboutus.backend.schedules.repository;

import com.howaboutus.backend.schedules.entity.ScheduleItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, Long> {

    @Query("select max(si.orderIndex) from ScheduleItem si where si.schedule.id = :scheduleId")
    Optional<Integer> findMaxOrderIndexBySchedule_Id(@Param("scheduleId") Long scheduleId);

    List<ScheduleItem> findAllBySchedule_IdOrderByOrderIndexAsc(Long scheduleId);

    Optional<ScheduleItem> findByIdAndSchedule_Id(Long itemId, Long scheduleId);

    void deleteAllBySchedule_Id(Long scheduleId);
}
```

주의: `findMaxOrderIndexBySchedule_Id`가 `@Query`를 사용하므로 `@Query`, `@Param` import는 유지. `@Modifying`과 `UUID` import만 제거.

최종 파일:
```java
package com.howaboutus.backend.schedules.repository;

import com.howaboutus.backend.schedules.entity.ScheduleItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, Long> {

    @Query("select max(si.orderIndex) from ScheduleItem si where si.schedule.id = :scheduleId")
    Optional<Integer> findMaxOrderIndexBySchedule_Id(@Param("scheduleId") Long scheduleId);

    List<ScheduleItem> findAllBySchedule_IdOrderByOrderIndexAsc(Long scheduleId);

    Optional<ScheduleItem> findByIdAndSchedule_Id(Long itemId, Long scheduleId);

    void deleteAllBySchedule_Id(Long scheduleId);
}
```

- [ ] **Step 2: ScheduleRepository에서 deleteAllByRoomId 제거**

`src/main/java/com/howaboutus/backend/schedules/repository/ScheduleRepository.java`에서 35-37행 삭제:
```java
// 삭제할 코드:
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Schedule s WHERE s.room.id = :roomId")
    void deleteAllByRoomId(@Param("roomId") UUID roomId);
```

`incrementVersionIfCurrent`가 `@Modifying`, `@Query`, `@Param`, `UUID`를 모두 사용하므로 import 변경 없음.

- [ ] **Step 3: BookmarkRepository에서 deleteAllByRoomId 제거**

`src/main/java/com/howaboutus/backend/bookmarks/repository/BookmarkRepository.java`에서 28-30행 삭제:
```java
// 삭제할 코드:
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Bookmark b WHERE b.room.id = :roomId")
    void deleteAllByRoomId(@Param("roomId") UUID roomId);
```

`@Modifying` import 제거 (다른 메서드에서 사용하지 않음):
```java
import org.springframework.data.jpa.repository.Modifying;
```

`@Query`, `@Param`, `UUID`는 `countGroupedByCategoryId`에서 사용하므로 유지.

- [ ] **Step 4: BookmarkCategoryRepository에서 deleteAllByRoomId 제거**

`src/main/java/com/howaboutus/backend/bookmarks/repository/BookmarkCategoryRepository.java`에서 22-24행 삭제:
```java
// 삭제할 코드:
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM BookmarkCategory bc WHERE bc.room.id = :roomId")
    void deleteAllByRoomId(@Param("roomId") UUID roomId);
```

`@Modifying`, `@Query`, `@Param` import 제거 (다른 메서드에서 사용하지 않음):
```java
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
```

- [ ] **Step 5: RoomMemberRepository에서 deleteByRoomId 제거**

`src/main/java/com/howaboutus/backend/rooms/repository/RoomMemberRepository.java`에서 35-40행 삭제:
```java
// 삭제할 코드 (주석 포함):
    //벌크 쿼리 사용
    //jpa에서 기본 deletedByRoomId를 사용하면, select이후, delete 쿼리가 실행되어, N+1문제가 발생함.
    //아래의 쿼리 형식을 사용하면, 1번의 쿼리로 삭제가 가능함.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RoomMember rm WHERE rm.room.id = :roomId")
    void deleteByRoomId(@Param("roomId") UUID roomId);
```

`@Modifying`, `@Query`, `@Param` import 제거 (다른 메서드에서 사용하지 않음):
```java
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
```

- [ ] **Step 6: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/schedules/repository/ScheduleItemRepository.java \
        src/main/java/com/howaboutus/backend/schedules/repository/ScheduleRepository.java \
        src/main/java/com/howaboutus/backend/bookmarks/repository/BookmarkRepository.java \
        src/main/java/com/howaboutus/backend/bookmarks/repository/BookmarkCategoryRepository.java \
        src/main/java/com/howaboutus/backend/rooms/repository/RoomMemberRepository.java
git commit -m "refactor: Room 삭제용 벌크 DELETE 메서드 제거"
```

---

### Task 4: 테스트 수정

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/rooms/service/RoomServiceTest.java`

- [ ] **Step 1: RoomServiceTest에서 불필요한 mock 의존성 제거 및 삭제 테스트 수정**

`src/test/java/com/howaboutus/backend/rooms/service/RoomServiceTest.java`에서:

import 제거 (16-17, 20-21행):
```java
// 삭제:
import com.howaboutus.backend.bookmarks.repository.BookmarkCategoryRepository;
import com.howaboutus.backend.bookmarks.repository.BookmarkRepository;
import com.howaboutus.backend.schedules.repository.ScheduleItemRepository;
import com.howaboutus.backend.schedules.repository.ScheduleRepository;
```

mock 필드 제거 (51-54행):
```java
// 삭제:
    @Mock private ScheduleItemRepository scheduleItemRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private BookmarkCategoryRepository bookmarkCategoryRepository;
```

`setUp()` 메서드(59-66행)를 수정:
```java
    @BeforeEach
    void setUp() {
        roomAuthorizationService = new RoomAuthorizationService(roomMemberRepository);
        roomService = new RoomService(roomRepository, roomMemberRepository,
                userRepository, inviteCodeGenerator, roomAuthorizationService);
    }
```

`deleteRoomSucceeds()` 테스트(290-313행)를 수정:
```java
    @Test
    @DisplayName("HOST가 방을 삭제하면 Room이 물리 삭제된다")
    void deleteRoomSucceeds() {
        UUID roomId = UUID.randomUUID();
        Long userId = 1L;
        Room room = Room.create("부산 여행", "부산", null, null, "aB3xK9mQ2w", userId);
        ReflectionTestUtils.setField(room, "id", roomId);

        User user = User.ofGoogle("google-id", "test@test.com", "테스터", null);
        ReflectionTestUtils.setField(user, "id", userId);
        RoomMember hostMember = RoomMember.of(room, user, RoomRole.HOST);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(roomMemberRepository.findByRoom_IdAndUser_Id(roomId, userId)).willReturn(Optional.of(hostMember));

        roomService.delete(roomId, userId);

        verify(roomRepository).delete(room);
    }
```

- [ ] **Step 2: 테스트 실행**

Run: `./gradlew test --tests "com.howaboutus.backend.rooms.service.RoomServiceTest"`
Expected: 모든 테스트 PASS

- [ ] **Step 3: 커밋**

```bash
git add src/test/java/com/howaboutus/backend/rooms/service/RoomServiceTest.java
git commit -m "test: RoomServiceTest를 DB CASCADE 방식에 맞게 수정"
```

---

### Task 5: 문서 업데이트

**Files:**
- Modify: `docs/ai/erd.md:215,225`
- Modify: `src/main/java/com/howaboutus/backend/rooms/controller/RoomController.java:87`

- [ ] **Step 1: erd.md 설계 포인트 8번 수정**

`docs/ai/erd.md`의 215행을 수정:

변경 전:
```
8. **방 Hard Delete:** 방 삭제 시 서비스 레이어에서 FK 순서에 맞게 ScheduleItem → Schedule → Bookmark → BookmarkCategory → RoomMember → Room을 순차 물리 삭제한다. JPA Cascade 대신 명시적 서비스 레이어 삭제로 단방향 관계를 유지한다.
```

변경 후:
```
8. **방 Hard Delete:** 모든 하위 엔티티 FK에 `@OnDelete(CASCADE)` (DB `ON DELETE CASCADE`)를 적용하여, `roomRepository.delete(room)` 한 줄로 Room과 하위 데이터를 삭제한다. 단방향 관계를 유지하면서 DB가 cascade 삭제를 처리한다.
```

- [ ] **Step 2: erd.md TODO 항목 수정**

`docs/ai/erd.md`의 225행을 수정:

변경 전:
```
- [x] 방 삭제 정책: hard delete 전환 완료 (서비스 레이어 명시적 삭제)
```

변경 후:
```
- [x] 방 삭제 정책: hard delete 전환 완료 (DB ON DELETE CASCADE)
```

- [ ] **Step 3: RoomController @Operation 주석 수정**

`src/main/java/com/howaboutus/backend/rooms/controller/RoomController.java`의 87행을 수정:

변경 전:
```java
    @Operation(summary = "방 삭제", description = "방을 삭제합니다 (soft delete). HOST만 가능합니다.")
```

변경 후:
```java
    @Operation(summary = "방 삭제", description = "방을 삭제합니다 (hard delete). HOST만 가능합니다.")
```

- [ ] **Step 4: 커밋**

```bash
git add docs/ai/erd.md \
        src/main/java/com/howaboutus/backend/rooms/controller/RoomController.java
git commit -m "docs: ERD 설계 포인트와 API 주석을 DB CASCADE 방식으로 갱신"
```

---

### Task 6: DB 마이그레이션 SQL 작성

**Files:**
- Create: `docs/sql/2026-04-28-add-on-delete-cascade.sql`

- [ ] **Step 1: 마이그레이션 SQL 파일 작성**

`docs/sql/2026-04-28-add-on-delete-cascade.sql` 생성:

```sql
-- Room 삭제 시 ON DELETE CASCADE 적용 마이그레이션
-- 실행 전 기존 FK constraint 이름을 확인하고 치환할 것
--
-- 확인 쿼리:
-- SELECT conname, conrelid::regclass, confrelid::regclass
-- FROM pg_constraint
-- WHERE contype = 'f'
--   AND conrelid::regclass::text IN ('room_members','schedules','schedule_items','bookmark_categories','bookmarks');

BEGIN;

-- 1. room_members.room_id → rooms.id
ALTER TABLE room_members
    DROP CONSTRAINT IF EXISTS fk_room_members_room_id,
    ADD CONSTRAINT fk_room_members_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE;

-- 2. schedules.room_id → rooms.id
ALTER TABLE schedules
    DROP CONSTRAINT IF EXISTS fk_schedules_room_id,
    ADD CONSTRAINT fk_schedules_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE;

-- 3. schedule_items.schedule_id → schedules.id
ALTER TABLE schedule_items
    DROP CONSTRAINT IF EXISTS fk_schedule_items_schedule_id,
    ADD CONSTRAINT fk_schedule_items_schedule FOREIGN KEY (schedule_id) REFERENCES schedules(id) ON DELETE CASCADE;

-- 4. bookmark_categories.room_id → rooms.id
ALTER TABLE bookmark_categories
    DROP CONSTRAINT IF EXISTS fk_bookmark_categories_room_id,
    ADD CONSTRAINT fk_bookmark_categories_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE;

-- 5. bookmarks.room_id → rooms.id
ALTER TABLE bookmarks
    DROP CONSTRAINT IF EXISTS fk_bookmarks_room_id,
    ADD CONSTRAINT fk_bookmarks_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE;

-- 6. bookmarks.(category_id, room_id) → bookmark_categories.(id, room_id)
ALTER TABLE bookmarks
    DROP CONSTRAINT IF EXISTS fk_bookmarks_category,
    ADD CONSTRAINT fk_bookmarks_category FOREIGN KEY (category_id, room_id) REFERENCES bookmark_categories(id, room_id) ON DELETE CASCADE;

COMMIT;
```

**중요:** `DROP CONSTRAINT`의 이름은 실제 DB에서 확인 후 교체해야 한다. 위의 확인 쿼리를 먼저 실행할 것.

- [ ] **Step 2: 커밋**

```bash
git add docs/sql/2026-04-28-add-on-delete-cascade.sql
git commit -m "docs: ON DELETE CASCADE 마이그레이션 SQL 작성"
```

---

### Task 7: 전체 테스트 실행 및 최종 확인

- [ ] **Step 1: 전체 테스트 실행**

Run: `./gradlew test`
Expected: 모든 테스트 PASS

- [ ] **Step 2: 실패 시 수정 후 재실행**

컴파일 에러나 테스트 실패가 있으면 해당 파일 수정 후 재실행.
