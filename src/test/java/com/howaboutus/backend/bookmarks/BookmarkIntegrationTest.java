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
    @DisplayName("방 ID로 조회하면 해당 방의 북마크만 반환한다")
    void savesBookmarkAndLoadsByRoomId() {
        Room targetRoom = roomRepository.save(Room.create(
                "도쿄 여행",
                "도쿄",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 3),
                "TOKYO2026",
                1L
        ));

        Room otherRoom = roomRepository.save(Room.create(
                "오사카 여행",
                "오사카",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                "OSAKA2026",
                2L
        ));

        bookmarkRepository.saveAndFlush(Bookmark.create(targetRoom, "place-1", "CAFE", 1L));
        bookmarkRepository.saveAndFlush(Bookmark.create(targetRoom, "place-2", "RESTAURANT", 1L));
        bookmarkRepository.saveAndFlush(Bookmark.create(otherRoom, "place-3", "HOTEL", 2L));

        List<Bookmark> bookmarks = bookmarkRepository.findAllByRoom_IdOrderByCreatedAtDesc(targetRoom.getId());

        assertThat(bookmarks).hasSize(2);
        assertThat(bookmarks).allSatisfy(bookmark -> assertThat(bookmark.getRoom().getId()).isEqualTo(targetRoom.getId()));
        assertThat(bookmarks).extracting(Bookmark::getGooglePlaceId).containsExactly("place-2", "place-1");
    }

    @Test
    @DisplayName("같은 googlePlaceId는 다른 방에서는 허용되고 같은 방에서는 중복 저장할 수 없다")
    void rejectsDuplicateGooglePlaceIdInSameRoom() {
        Room targetRoom = roomRepository.save(Room.create(
                "오사카 여행",
                "오사카",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 2),
                "OSAKA2027",
                2L
        ));

        Room otherRoom = roomRepository.save(Room.create(
                "후쿠오카 여행",
                "후쿠오카",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 2),
                "FUKUOKA2027",
                3L
        ));

        bookmarkRepository.saveAndFlush(Bookmark.create(otherRoom, "place-1", "ALL", 3L));
        bookmarkRepository.saveAndFlush(Bookmark.create(targetRoom, "place-1", "ALL", 2L));

        assertThatThrownBy(() -> bookmarkRepository.saveAndFlush(Bookmark.create(targetRoom, "place-1", "ALL", 2L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
