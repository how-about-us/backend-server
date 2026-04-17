package com.howaboutus.backend.bookmarks.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.bookmarks.entity.Bookmark;
import com.howaboutus.backend.bookmarks.repository.BookmarkRepository;
import com.howaboutus.backend.bookmarks.service.dto.BookmarkCreateCommand;
import com.howaboutus.backend.bookmarks.service.dto.BookmarkResult;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BookmarkRepository bookmarkRepository;

    private BookmarkService bookmarkService;

    @BeforeEach
    void setUp() {
        bookmarkService = new BookmarkService(roomRepository, bookmarkRepository);
    }

    @Test
    @DisplayName("카테고리가 null이면 생성 시 기본값 ALL이 저장된다")
    void createUsesDefaultCategoryWhenCategoryIsNull() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);
        Bookmark savedBookmark = Bookmark.create(room, "place-1", null, null);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkRepository.existsByRoom_IdAndGooglePlaceId(roomId, "place-1")).willReturn(false);
        given(bookmarkRepository.save(any(Bookmark.class))).willReturn(savedBookmark);

        BookmarkResult result = bookmarkService.create(roomId, new BookmarkCreateCommand("place-1", null));

        assertThat(result.category()).isEqualTo(Bookmark.DEFAULT_CATEGORY);

        ArgumentCaptor<Bookmark> bookmarkCaptor = ArgumentCaptor.forClass(Bookmark.class);
        verify(bookmarkRepository).save(bookmarkCaptor.capture());
        assertThat(bookmarkCaptor.getValue().getCategory()).isEqualTo(Bookmark.DEFAULT_CATEGORY);
        assertThat(bookmarkCaptor.getValue().getAddedBy()).isNull();
    }

    @Test
    @DisplayName("방이 없으면 생성 시 ROOM_NOT_FOUND 예외를 던진다")
    void createThrowsWhenRoomMissing() {
        UUID roomId = UUID.randomUUID();

        given(roomRepository.findById(roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookmarkService.create(roomId, new BookmarkCreateCommand("place-1", "CAFE")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("같은 방에 같은 googlePlaceId가 있으면 BOOKMARK_ALREADY_EXISTS 예외를 던진다")
    void createThrowsWhenDuplicateBookmarkExists() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkRepository.existsByRoom_IdAndGooglePlaceId(roomId, "place-1")).willReturn(true);

        assertThatThrownBy(() -> bookmarkService.create(roomId, new BookmarkCreateCommand("place-1", "CAFE")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKMARK_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("방이 없으면 목록 조회 시 ROOM_NOT_FOUND 예외를 던진다")
    void getBookmarksThrowsWhenRoomMissing() {
        UUID roomId = UUID.randomUUID();

        given(roomRepository.findById(roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookmarkService.getBookmarks(roomId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("방의 북마크 목록 조회 시 BookmarkResult로 매핑된 결과를 반환한다")
    void getBookmarksReturnsMappedResults() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);
        Bookmark bookmark = Bookmark.create(room, "place-1", "CAFE", null);

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(bookmark, "id", 10L);
        ReflectionTestUtils.setField(bookmark, "createdAt", Instant.parse("2026-04-17T00:00:00Z"));

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkRepository.findAllByRoom_IdOrderByCreatedAtDesc(roomId)).willReturn(List.of(bookmark));

        List<BookmarkResult> results = bookmarkService.getBookmarks(roomId);

        assertThat(results).containsExactly(BookmarkResult.from(bookmark));
    }

    @Test
    @DisplayName("방 밖의 북마크를 삭제하려고 하면 BOOKMARK_NOT_FOUND 예외를 던진다")
    void deleteThrowsWhenBookmarkOutsideRoom() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkRepository.findByIdAndRoom_Id(10L, roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookmarkService.delete(roomId, 10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKMARK_NOT_FOUND);
    }

    @Test
    @DisplayName("방의 북마크 삭제 시 repository.delete를 호출한다")
    void deleteRemovesBookmark() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);
        Bookmark bookmark = Bookmark.create(room, "place-1", "CAFE", null);

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(bookmark, "id", 10L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkRepository.findByIdAndRoom_Id(10L, roomId)).willReturn(Optional.of(bookmark));

        bookmarkService.delete(roomId, 10L);

        verify(bookmarkRepository).delete(bookmark);
    }

    @Test
    @DisplayName("저장 중 DB 중복이 발생하면 BOOKMARK_ALREADY_EXISTS 예외로 변환한다")
    void createTranslatesDatabaseDuplicateOnSave() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkRepository.existsByRoom_IdAndGooglePlaceId(roomId, "place-1")).willReturn(false);
        given(bookmarkRepository.save(any(Bookmark.class)))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> bookmarkService.create(roomId, new BookmarkCreateCommand("place-1", "CAFE")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKMARK_ALREADY_EXISTS);
    }
}
