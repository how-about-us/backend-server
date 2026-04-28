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
