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
DROP CONSTRAINT IF EXISTS fk1bbl9rh6ae8v6mebaoq2ilg9g,
    ADD CONSTRAINT fk_room_members_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE;

-- 2. schedules.room_id → rooms.id
ALTER TABLE schedules
DROP CONSTRAINT IF EXISTS fk34r5t4jexlcas19pleifb8ihv,
    ADD CONSTRAINT fk_schedules_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE;

-- 3. schedule_items.schedule_id → schedules.id
ALTER TABLE schedule_items
DROP CONSTRAINT IF EXISTS fkr6n4sale56c2ae5oxkai72883,
    ADD CONSTRAINT fk_schedule_items_schedule FOREIGN KEY (schedule_id) REFERENCES schedules(id) ON DELETE CASCADE;

-- 4. bookmark_categories.room_id → rooms.id
ALTER TABLE bookmark_categories
DROP CONSTRAINT IF EXISTS fkcrs166k3lm37v0vhnu4xo9v1w,
    ADD CONSTRAINT fk_bookmark_categories_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE;

-- 5. bookmarks.room_id → rooms.id
ALTER TABLE bookmarks
DROP CONSTRAINT IF EXISTS fkkt5wbda38hbtkr813jtgftu5c,
    ADD CONSTRAINT fk_bookmarks_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE;

-- 6. bookmarks.(category_id, room_id) → bookmark_categories.(id, room_id)
ALTER TABLE bookmarks
DROP CONSTRAINT IF EXISTS fk89v2xqm3xsv0jm6ytwkpnjw5q,
    ADD CONSTRAINT fk_bookmarks_category FOREIGN KEY (category_id, room_id) REFERENCES bookmark_categories(id, room_id) ON DELETE CASCADE;

COMMIT;
