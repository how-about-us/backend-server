package com.howaboutus.backend.bookmarks.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.bookmarks.entity.BookmarkCategory;
import com.howaboutus.backend.bookmarks.repository.BookmarkCategoryRepository;
import com.howaboutus.backend.bookmarks.service.dto.BookmarkCategoryCreateCommand;
import com.howaboutus.backend.bookmarks.service.dto.BookmarkCategoryRenameCommand;
import com.howaboutus.backend.bookmarks.service.dto.BookmarkCategoryResult;
import com.howaboutus.backend.bookmarks.repository.BookmarkRepository;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BookmarkCategoryServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BookmarkCategoryRepository bookmarkCategoryRepository;

    @Mock
    private BookmarkRepository bookmarkRepository;

    private BookmarkCategoryService bookmarkCategoryService;

    @BeforeEach
    void setUp() {
        bookmarkCategoryService = new BookmarkCategoryService(roomRepository, bookmarkCategoryRepository, bookmarkRepository);
    }

    @Test
    @DisplayName("카테고리 생성 성공 시 저장된 값을 반환한다")
    void createReturnsSavedCategory() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);
        BookmarkCategory savedCategory = BookmarkCategory.create(room, "맛집", "#FF8800", null);

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(savedCategory, "id", 10L);
        ReflectionTestUtils.setField(savedCategory, "createdAt", Instant.parse("2026-04-17T00:00:00Z"));

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkCategoryRepository.existsByRoom_IdAndName(roomId, "맛집")).willReturn(false);
        given(bookmarkCategoryRepository.saveAndFlush(any(BookmarkCategory.class))).willReturn(savedCategory);

        BookmarkCategoryResult result = bookmarkCategoryService.create(roomId, new BookmarkCategoryCreateCommand("맛집", "#FF8800"));

        assertThat(result).isEqualTo(BookmarkCategoryResult.from(savedCategory));

        ArgumentCaptor<BookmarkCategory> captor = ArgumentCaptor.forClass(BookmarkCategory.class);
        verify(bookmarkCategoryRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getRoom()).isSameAs(room);
        assertThat(captor.getValue().getColorCode()).isEqualTo("#FF8800");
        assertThat(captor.getValue().getCreatedBy()).isNull();
    }

    @Test
    @DisplayName("같은 방에 같은 이름이 있으면 생성 시 BOOKMARK_CATEGORY_ALREADY_EXISTS 예외를 던진다")
    void createThrowsWhenDuplicateCategoryExists() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkCategoryRepository.existsByRoom_IdAndName(roomId, "맛집")).willReturn(true);

        assertThatThrownBy(() -> bookmarkCategoryService.create(roomId, new BookmarkCategoryCreateCommand("맛집", "#FF8800")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKMARK_CATEGORY_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("방이 없으면 목록 조회 시 ROOM_NOT_FOUND 예외를 던진다")
    void getCategoriesThrowsWhenRoomMissing() {
        UUID roomId = UUID.randomUUID();

        given(roomRepository.findById(roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookmarkCategoryService.getCategories(roomId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("카테고리 목록은 생성 시각 오름차순으로 반환된다")
    void getCategoriesReturnsSortedResults() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);
        BookmarkCategory first = BookmarkCategory.create(room, "맛집", "#FF8800", null);
        BookmarkCategory second = BookmarkCategory.create(room, "카페", "#3366FF", null);

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(first, "id", 10L);
        ReflectionTestUtils.setField(first, "createdAt", Instant.parse("2026-04-17T00:00:00Z"));
        ReflectionTestUtils.setField(second, "id", 11L);
        ReflectionTestUtils.setField(second, "createdAt", Instant.parse("2026-04-18T00:00:00Z"));

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkCategoryRepository.findAllByRoom_IdOrderByCreatedAtAsc(roomId)).willReturn(List.of(first, second));

        List<BookmarkCategoryResult> results = bookmarkCategoryService.getCategories(roomId);

        assertThat(results).containsExactly(
                BookmarkCategoryResult.from(first),
                BookmarkCategoryResult.from(second)
        );
    }

    @Test
    @DisplayName("카테고리 이름 변경 성공 시 저장된 값을 반환한다")
    void renameReturnsSavedCategory() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);
        BookmarkCategory category = BookmarkCategory.create(room, "맛집", "#FF8800", null);

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(category, "id", 10L);
        ReflectionTestUtils.setField(category, "createdAt", Instant.parse("2026-04-17T00:00:00Z"));

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkCategoryRepository.findByIdAndRoom_Id(10L, roomId)).willReturn(Optional.of(category));
        given(bookmarkCategoryRepository.existsByRoom_IdAndName(roomId, "카페")).willReturn(false);
        given(bookmarkCategoryRepository.saveAndFlush(any(BookmarkCategory.class))).willReturn(category);

        BookmarkCategoryResult result = bookmarkCategoryService.rename(roomId, 10L, new BookmarkCategoryRenameCommand("카페", "#3366FF"));

        assertThat(result).isEqualTo(BookmarkCategoryResult.from(category));
        assertThat(category.getName()).isEqualTo("카페");
        assertThat(category.getColorCode()).isEqualTo("#3366FF");
    }

    @Test
    @DisplayName("방 밖의 카테고리를 수정하려고 하면 BOOKMARK_CATEGORY_NOT_FOUND 예외를 던진다")
    void renameThrowsWhenCategoryOutsideRoom() {
        UUID roomId = UUID.randomUUID();
        UUID otherRoomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);
        Room otherRoom = Room.create("오사카 여행", "오사카", null, null, "INVITE-2", 2L);
        BookmarkCategory category = BookmarkCategory.create(otherRoom, "맛집", "#FF8800", null);

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(otherRoom, "id", otherRoomId);
        ReflectionTestUtils.setField(category, "id", 10L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkCategoryRepository.findByIdAndRoom_Id(10L, roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookmarkCategoryService.rename(roomId, 10L, new BookmarkCategoryRenameCommand("카페", "#3366FF")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKMARK_CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("같은 방에 같은 이름이 있으면 수정 시 BOOKMARK_CATEGORY_ALREADY_EXISTS 예외를 던진다")
    void renameThrowsWhenDuplicateTargetNameExists() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);
        BookmarkCategory category = BookmarkCategory.create(room, "맛집", "#FF8800", null);

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(category, "id", 10L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkCategoryRepository.findByIdAndRoom_Id(10L, roomId)).willReturn(Optional.of(category));
        given(bookmarkCategoryRepository.existsByRoom_IdAndName(roomId, "카페")).willReturn(true);

        assertThatThrownBy(() -> bookmarkCategoryService.rename(roomId, 10L, new BookmarkCategoryRenameCommand("카페", "#3366FF")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKMARK_CATEGORY_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("카테고리 삭제 성공 시 repository.delete를 호출한다")
    void deleteRemovesCategory() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);
        BookmarkCategory category = BookmarkCategory.create(room, "맛집", "#FF8800", null);

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(category, "id", 10L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkCategoryRepository.findByIdAndRoom_Id(10L, roomId)).willReturn(Optional.of(category));

        bookmarkCategoryService.delete(roomId, 10L);

        InOrder inOrder = org.mockito.Mockito.inOrder(bookmarkRepository, bookmarkCategoryRepository);
        inOrder.verify(bookmarkRepository).deleteAllByCategory_Id(10L);
        inOrder.verify(bookmarkCategoryRepository).delete(category);
    }

    @Test
    @DisplayName("방 밖의 카테고리를 삭제하려고 하면 BOOKMARK_CATEGORY_NOT_FOUND 예외를 던진다")
    void deleteThrowsWhenCategoryOutsideRoom() {
        UUID roomId = UUID.randomUUID();
        UUID otherRoomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);
        Room otherRoom = Room.create("오사카 여행", "오사카", null, null, "INVITE-2", 2L);
        BookmarkCategory category = BookmarkCategory.create(otherRoom, "맛집", "#FF8800", null);

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(otherRoom, "id", otherRoomId);
        ReflectionTestUtils.setField(category, "id", 10L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkCategoryRepository.findByIdAndRoom_Id(10L, roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookmarkCategoryService.delete(roomId, 10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKMARK_CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("저장 중 DB 중복이 발생하면 BOOKMARK_CATEGORY_ALREADY_EXISTS 예외로 변환한다")
    void createTranslatesDatabaseDuplicateOnSave() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkCategoryRepository.existsByRoom_IdAndName(roomId, "맛집")).willReturn(false);
        given(bookmarkCategoryRepository.saveAndFlush(any(BookmarkCategory.class)))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> bookmarkCategoryService.create(roomId, new BookmarkCategoryCreateCommand("맛집", "#FF8800")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKMARK_CATEGORY_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("수정 중 DB 중복이 발생하면 BOOKMARK_CATEGORY_ALREADY_EXISTS 예외로 변환한다")
    void renameTranslatesDatabaseDuplicateOnSave() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", null, null, "INVITE", 1L);
        BookmarkCategory category = BookmarkCategory.create(room, "맛집", "#FF8800", null);

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(category, "id", 10L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(bookmarkCategoryRepository.findByIdAndRoom_Id(10L, roomId)).willReturn(Optional.of(category));
        given(bookmarkCategoryRepository.existsByRoom_IdAndName(roomId, "카페")).willReturn(false);
        given(bookmarkCategoryRepository.saveAndFlush(any(BookmarkCategory.class)))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> bookmarkCategoryService.rename(roomId, 10L, new BookmarkCategoryRenameCommand("카페", "#3366FF")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKMARK_CATEGORY_ALREADY_EXISTS);
    }
}
