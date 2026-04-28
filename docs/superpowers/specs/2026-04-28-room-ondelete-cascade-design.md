# Room 삭제: @OnDelete(CASCADE) 전환 설계

## 배경

현재 Room 삭제 시 서비스 레이어에서 FK 순서에 맞게 6개 Repository를 순차 호출하는 벌크 DELETE 방식을 사용 중이다.

문제점:
- 엔티티가 추가될 때마다 `RoomService.delete()`와 Repository에 삭제 메서드를 추가해야 함 (높은 의존도)
- FK 삭제 순서를 항상 고려해야 함 (실수 가능성)
- JPA 정합성 문제가 발생할 가능성이 낮아 DB 레벨 cascade로 충분함

## 결정

`@OnDelete(action = OnDeleteAction.CASCADE)`를 사용하여 DB 레벨 `ON DELETE CASCADE`로 전환한다.

## 변경 범위

### 1. 엔티티 변경

아래 `@ManyToOne` FK에 `@OnDelete(action = OnDeleteAction.CASCADE)` 추가:

| 엔티티 | FK 필드 | 참조 대상 |
|--------|---------|----------|
| RoomMember | `room` | Room |
| Schedule | `room` | Room |
| ScheduleItem | `schedule` | Schedule |
| BookmarkCategory | `room` | Room |
| Bookmark | `room` | Room |
| Bookmark | `category` (복합FK) | BookmarkCategory |

제외: `RoomMember.user` FK에는 CASCADE를 걸지 않는다. User 삭제(회원 탈퇴) 정책은 별도 설계로 분리한다.

### 2. 서비스 레이어 변경

`RoomService.delete()` 메서드를 `roomRepository.delete(room)` 한 줄로 단순화한다.

불필요해지는 의존성 제거:
- `ScheduleItemRepository`
- `ScheduleRepository`
- `BookmarkRepository`
- `BookmarkCategoryRepository`

`RoomMemberRepository`는 다른 메서드에서 사용 중이므로 유지.

### 3. Repository 정리

Room 삭제 전용 벌크 DELETE 메서드 제거:

| Repository | 제거 메서드 |
|-----------|-----------|
| ScheduleItemRepository | `deleteAllByRoomId(UUID)` |
| ScheduleRepository | `deleteAllByRoomId(UUID)` |
| BookmarkRepository | `deleteAllByRoomId(UUID)` |
| BookmarkCategoryRepository | `deleteAllByRoomId(UUID)` |
| RoomMemberRepository | `deleteByRoomId(UUID)` |

다른 용도로 사용 중인 메서드는 유지 (예: `deleteAllBySchedule_Id()`, `deleteAllByCategory_Id()`).

### 4. DB 마이그레이션

수동 SQL로 기존 FK를 DROP 후 `ON DELETE CASCADE`로 재생성:

- `room_members.room_id → rooms.id`
- `schedules.room_id → rooms.id`
- `schedule_items.schedule_id → schedules.id`
- `bookmark_categories.room_id → rooms.id`
- `bookmarks.room_id → rooms.id`
- `bookmarks.(category_id, room_id) → bookmark_categories.(id, room_id)`

### 5. 테스트 변경

`RoomServiceTest.deleteRoomSucceeds()`:
- 6개 Repository verify → `verify(roomRepository).delete(room)` 1줄로 축소
- 불필요한 mock 의존성 제거

### 6. 문서 업데이트

- `docs/ai/erd.md` 설계 포인트 8번: DB 레벨 cascade 방식으로 수정
- `RoomController` `@Operation` 주석: "soft delete" → "hard delete" 수정

## Bookmark 복합 FK 참고

Bookmark은 `room_id` FK와 `(category_id, room_id)` 복합 FK 양쪽 모두 `ON DELETE CASCADE`를 갖는다. Room 삭제 시 두 경로로 cascade가 발생하지만, PostgreSQL은 이미 삭제된 행을 무시하므로 정상 동작한다.
