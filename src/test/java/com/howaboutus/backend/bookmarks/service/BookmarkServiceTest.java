package com.howaboutus.backend.bookmarks.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.bookmarkcategories.entity.BookmarkCategory;
import com.howaboutus.backend.bookmarkcategories.repository.BookmarkCategoryRepository;
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

    @Mock
    private BookmarkCategoryRepository bookmarkCategoryRepository;

    private BookmarkService bookmarkService;

    @BeforeEach
    void setUp() {
        bookmarkService = new BookmarkService(roomRepository, bookmarkRepository, bookmarkCategoryRepository);
    }

    @Test
    @DisplayName("북마크 생성 성공 시 저장된 값을 반환한다")
    void createReturnsSavedBookmarkWithCategory() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);
        BookmarkCategory category = BookmarkCategory.create(room, "맛집", null);
        Bookmark savedBookmark = Bookmark.create(room, "place-1", category, null);

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(category, "id", 10L);
        ReflectionTestUtils.setField(savedBookmark, "id", 11L);
        ReflectionTestUtils.setField(savedBookmark, "createdAt", Instant.parse("2026-04-17T00:00:00Z"));

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkCategoryRepository.existsByRoom_Id(roomId)).willReturn(true);
        given(bookmarkCategoryRepository.findByIdAndRoom_Id(10L, roomId)).willReturn(Optional.of(category));
        given(bookmarkRepository.existsByRoom_IdAndGooglePlaceId(roomId, "place-1")).willReturn(false);
        given(bookmarkRepository.saveAndFlush(any(Bookmark.class))).willReturn(savedBookmark);

        BookmarkResult result = bookmarkService.create(roomId, new BookmarkCreateCommand("place-1", 10L));

        assertThat(result).isEqualTo(BookmarkResult.from(savedBookmark));

        ArgumentCaptor<Bookmark> bookmarkCaptor = ArgumentCaptor.forClass(Bookmark.class);
        verify(bookmarkRepository).saveAndFlush(bookmarkCaptor.capture());
        assertThat(bookmarkCaptor.getValue().getCategory()).isSameAs(category);
        assertThat(bookmarkCaptor.getValue().getAddedBy()).isNull();
    }

    @Test
    @DisplayName("방이 없으면 생성 시 ROOM_NOT_FOUND 예외를 던진다")
    void createThrowsWhenRoomMissing() {
        UUID roomId = UUID.randomUUID();

        given(roomRepository.findById(roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookmarkService.create(roomId, new BookmarkCreateCommand("place-1", 10L)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("방에 카테고리가 없으면 생성 시 BOOKMARK_CATEGORY_EMPTY 예외를 던진다")
    void createThrowsWhenRoomHasNoCategories() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkCategoryRepository.existsByRoom_Id(roomId)).willReturn(false);

        assertThatThrownBy(() -> bookmarkService.create(roomId, new BookmarkCreateCommand("place-1", 10L)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKMARK_CATEGORY_EMPTY);
    }

    @Test
    @DisplayName("방 밖의 카테고리로 생성하려고 하면 BOOKMARK_CATEGORY_NOT_FOUND 예외를 던진다")
    void createThrowsWhenCategoryOutsideRoom() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkCategoryRepository.existsByRoom_Id(roomId)).willReturn(true);
        given(bookmarkCategoryRepository.findByIdAndRoom_Id(10L, roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookmarkService.create(roomId, new BookmarkCreateCommand("place-1", 10L)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKMARK_CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("같은 방에 같은 googlePlaceId가 있으면 BOOKMARK_ALREADY_EXISTS 예외를 던진다")
    void createThrowsWhenDuplicateBookmarkExists() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);
        BookmarkCategory category = BookmarkCategory.create(room, "맛집", null);

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(category, "id", 10L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkCategoryRepository.existsByRoom_Id(roomId)).willReturn(true);
        given(bookmarkCategoryRepository.findByIdAndRoom_Id(10L, roomId)).willReturn(Optional.of(category));
        given(bookmarkRepository.existsByRoom_IdAndGooglePlaceId(roomId, "place-1")).willReturn(true);

        assertThatThrownBy(() -> bookmarkService.create(roomId, new BookmarkCreateCommand("place-1", 10L)))
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
        BookmarkCategory category = BookmarkCategory.create(room, "카페", null);
        Bookmark bookmark = Bookmark.create(room, "place-1", category, null);

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(category, "id", 20L);
        ReflectionTestUtils.setField(bookmark, "id", 10L);
        ReflectionTestUtils.setField(bookmark, "createdAt", Instant.parse("2026-04-17T00:00:00Z"));

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkRepository.findAllByRoom_IdOrderByCreatedAtDesc(roomId)).willReturn(List.of(bookmark));

        List<BookmarkResult> results = bookmarkService.getBookmarks(roomId);

        assertThat(results).containsExactly(BookmarkResult.from(bookmark));
        assertThat(results.getFirst().categoryId()).isEqualTo(20L);
        assertThat(results.getFirst().category()).isEqualTo("카페");
    }

    @Test
    @DisplayName("북마크 카테고리 변경 성공 시 저장된 값을 반환한다")
    void updateCategoryReturnsSavedBookmark() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);
        BookmarkCategory oldCategory = BookmarkCategory.create(room, "맛집", null);
        BookmarkCategory newCategory = BookmarkCategory.create(room, "카페", null);
        Bookmark bookmark = Bookmark.create(room, "place-1", oldCategory, null);

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(oldCategory, "id", 10L);
        ReflectionTestUtils.setField(newCategory, "id", 11L);
        ReflectionTestUtils.setField(bookmark, "id", 12L);
        ReflectionTestUtils.setField(bookmark, "createdAt", Instant.parse("2026-04-17T00:00:00Z"));

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkRepository.findByIdAndRoom_Id(12L, roomId)).willReturn(Optional.of(bookmark));
        given(bookmarkCategoryRepository.findByIdAndRoom_Id(11L, roomId)).willReturn(Optional.of(newCategory));
        given(bookmarkRepository.saveAndFlush(any(Bookmark.class))).willReturn(bookmark);

        BookmarkResult result = bookmarkService.updateCategory(roomId, 12L, 11L);

        assertThat(result.categoryId()).isEqualTo(11L);
        assertThat(result.category()).isEqualTo("카페");
        assertThat(bookmark.getCategory()).isSameAs(newCategory);
        verify(bookmarkRepository).saveAndFlush(bookmark);
    }

    @Test
    @DisplayName("북마크 카테고리가 없으면 변경 시 BOOKMARK_CATEGORY_NOT_FOUND 예외를 던진다")
    void updateCategoryThrowsWhenCategoryMissing() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);
        BookmarkCategory category = BookmarkCategory.create(room, "맛집", null);
        Bookmark bookmark = Bookmark.create(room, "place-1", category, null);

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(category, "id", 10L);
        ReflectionTestUtils.setField(bookmark, "id", 12L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkRepository.findByIdAndRoom_Id(12L, roomId)).willReturn(Optional.of(bookmark));
        given(bookmarkCategoryRepository.findByIdAndRoom_Id(11L, roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookmarkService.updateCategory(roomId, 12L, 11L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKMARK_CATEGORY_NOT_FOUND);
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
        BookmarkCategory category = BookmarkCategory.create(room, "카페", null);
        Bookmark bookmark = Bookmark.create(room, "place-1", category, null);

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(category, "id", 10L);
        ReflectionTestUtils.setField(bookmark, "id", 11L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkRepository.findByIdAndRoom_Id(11L, roomId)).willReturn(Optional.of(bookmark));

        bookmarkService.delete(roomId, 11L);

        verify(bookmarkRepository).delete(bookmark);
    }

    @Test
    @DisplayName("저장 중 DB 중복이 발생하면 BOOKMARK_ALREADY_EXISTS 예외로 변환한다")
    void createTranslatesDatabaseDuplicateOnSave() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);
        BookmarkCategory category = BookmarkCategory.create(room, "맛집", null);

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(category, "id", 10L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkCategoryRepository.existsByRoom_Id(roomId)).willReturn(true);
        given(bookmarkCategoryRepository.findByIdAndRoom_Id(10L, roomId)).willReturn(Optional.of(category));
        given(bookmarkRepository.existsByRoom_IdAndGooglePlaceId(roomId, "place-1")).willReturn(false);
        given(bookmarkRepository.saveAndFlush(any(Bookmark.class)))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> bookmarkService.create(roomId, new BookmarkCreateCommand("place-1", 10L)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKMARK_ALREADY_EXISTS);
    }
}
