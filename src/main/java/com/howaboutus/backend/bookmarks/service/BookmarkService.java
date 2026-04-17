package com.howaboutus.backend.bookmarks.service;

import com.howaboutus.backend.bookmarks.entity.Bookmark;
import com.howaboutus.backend.bookmarks.repository.BookmarkRepository;
import com.howaboutus.backend.bookmarks.service.dto.BookmarkCreateCommand;
import com.howaboutus.backend.bookmarks.service.dto.BookmarkResult;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.repository.RoomRepository;

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

    // TODO: 나중에 Room 관련 로직이 만들어지면 그때 의존성을 바꾸짜
    private final RoomRepository roomRepository;
    private final BookmarkRepository bookmarkRepository;

    @Transactional
    public BookmarkResult create(UUID roomId, BookmarkCreateCommand command) {
        Room room = getRoom(roomId);
        if (bookmarkRepository.existsByRoom_IdAndGooglePlaceId(roomId, command.googlePlaceId())) {
            throw new CustomException(ErrorCode.BOOKMARK_ALREADY_EXISTS);
        }

        Bookmark bookmark = Bookmark.create(room, command.googlePlaceId(), command.category(), null);
        try {
            return BookmarkResult.from(bookmarkRepository.saveAndFlush(bookmark));
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.BOOKMARK_ALREADY_EXISTS, e);
        }
    }

    public List<BookmarkResult> getBookmarks(UUID roomId) {
        getRoom(roomId);
        return bookmarkRepository.findAllByRoom_IdOrderByCreatedAtDesc(roomId)
                .stream()
                .map(BookmarkResult::from)
                .toList();
    }

    @Transactional
    public void delete(UUID roomId, Long bookmarkId) {
        getRoom(roomId);
        Bookmark bookmark = bookmarkRepository.findByIdAndRoom_Id(bookmarkId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOKMARK_NOT_FOUND));
        bookmarkRepository.delete(bookmark);
    }

    private Room getRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
    }
}
