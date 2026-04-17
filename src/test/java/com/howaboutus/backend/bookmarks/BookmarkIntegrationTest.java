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
import org.springframework.dao.DataIntegrityViolationException;

class BookmarkIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Test
    @DisplayName("북마크를 방에 저장하고 방 ID로 조회한다")
    void savesBookmarkAndLoadsByRoomId() {
        Room room = roomRepository.save(Room.create(
                "도쿄 여행",
                "도쿄",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 3),
                "TOKYO2026",
                1L
        ));

        bookmarkRepository.saveAndFlush(Bookmark.create(room, "place-1", "CAFE", 1L));

        List<Bookmark> bookmarks = bookmarkRepository.findAllByRoom_IdOrderByCreatedAtDesc(room.getId());

        assertThat(bookmarks).hasSize(1);
        assertThat(bookmarks.get(0).getRoom().getId()).isEqualTo(room.getId());
        assertThat(bookmarks.get(0).getGooglePlaceId()).isEqualTo("place-1");
    }

    @Test
    @DisplayName("같은 방에서 동일한 googlePlaceId는 중복 저장할 수 없다")
    void rejectsDuplicateGooglePlaceIdInSameRoom() {
        Room room = roomRepository.save(Room.create(
                "오사카 여행",
                "오사카",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 2),
                "OSAKA2026",
                2L
        ));

        bookmarkRepository.saveAndFlush(Bookmark.create(room, "place-1", "ALL", 2L));

        assertThatThrownBy(() -> bookmarkRepository.saveAndFlush(Bookmark.create(room, "place-1", "ALL", 2L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
