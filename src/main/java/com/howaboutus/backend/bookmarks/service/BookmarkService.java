package com.howaboutus.backend.bookmarks.service;

import com.howaboutus.backend.bookmarks.entity.Bookmark;
import com.howaboutus.backend.bookmarks.repository.BookmarkRepository;
import com.howaboutus.backend.bookmarks.service.dto.BookmarkCreateCommand;
import com.howaboutus.backend.bookmarks.service.dto.BookmarkResult;
import com.howaboutus.backend.bookmarks.entity.BookmarkCategory;
import com.howaboutus.backend.bookmarks.repository.BookmarkCategoryRepository;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.rooms.service.RoomAuthorizationService;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkService {

    // TODO: 나중에 Room 관련 로직이 만들어지면 그때 의존성을 바꾸자
    private final RoomRepository roomRepository;
    private final BookmarkRepository bookmarkRepository;
    private final BookmarkCategoryRepository bookmarkCategoryRepository;
    private final RoomAuthorizationService roomAuthorizationService;

    @Transactional
    public BookmarkResult create(UUID roomId, BookmarkCreateCommand command, Long userId) {
        Room room = getRoom(roomId);
        roomAuthorizationService.requireActiveMember(roomId, userId);
        if (!bookmarkCategoryRepository.existsByRoom_Id(roomId)) {
            throw new CustomException(ErrorCode.BOOKMARK_CATEGORY_EMPTY);
        }

        BookmarkCategory category = getCategoryInRoom(roomId, command.categoryId());
        if (bookmarkRepository.existsByRoom_IdAndGooglePlaceId(roomId, command.googlePlaceId())) {
            throw new CustomException(ErrorCode.BOOKMARK_ALREADY_EXISTS);
        }

        Bookmark bookmark = Bookmark.create(room, command.googlePlaceId(), category, null);
        try {
            return BookmarkResult.from(bookmarkRepository.saveAndFlush(bookmark));
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.BOOKMARK_ALREADY_EXISTS, e);
        }
    }

    public List<BookmarkResult> getBookmarks(UUID roomId, long categoryId, Long userId) {
        getRoom(roomId);
        roomAuthorizationService.requireActiveMember(roomId, userId);
        return bookmarkRepository.findAllByRoom_IdAndCategory_IdOrderByCreatedAtDesc(roomId, categoryId)
                .stream()
                .map(BookmarkResult::from)
                .toList();
    }

    @Transactional
    public void delete(UUID roomId, long bookmarkId, Long userId) {
        getRoom(roomId);
        roomAuthorizationService.requireActiveMember(roomId, userId);
        Bookmark bookmark = bookmarkRepository.findByIdAndRoom_Id(bookmarkId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOKMARK_NOT_FOUND));
        bookmarkRepository.delete(bookmark);
    }

    @Transactional
    public BookmarkResult updateCategory(UUID roomId, long bookmarkId, long categoryId, Long userId) {
        getRoom(roomId);
        roomAuthorizationService.requireActiveMember(roomId, userId);
        Bookmark bookmark = bookmarkRepository.findByIdAndRoom_Id(bookmarkId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOKMARK_NOT_FOUND));
        BookmarkCategory category = getCategoryInRoom(roomId, categoryId);

        bookmark.changeCategory(category);
        return BookmarkResult.from(bookmarkRepository.saveAndFlush(bookmark));
    }

    private Room getRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
    }

    private BookmarkCategory getCategoryInRoom(UUID roomId, Long categoryId) {
        return bookmarkCategoryRepository.findByIdAndRoom_Id(categoryId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOKMARK_CATEGORY_NOT_FOUND));
    }
}
