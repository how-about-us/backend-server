package com.howaboutus.backend.bookmarks.service;

import com.howaboutus.backend.bookmarks.entity.BookmarkCategory;
import com.howaboutus.backend.bookmarks.repository.BookmarkCategoryRepository;
import com.howaboutus.backend.bookmarks.service.dto.BookmarkCategoryCreateCommand;
import com.howaboutus.backend.bookmarks.service.dto.BookmarkCategoryRenameCommand;
import com.howaboutus.backend.bookmarks.service.dto.BookmarkCategoryResult;
import com.howaboutus.backend.bookmarks.repository.BookmarkRepository;
import com.howaboutus.backend.bookmarks.repository.dto.CategoryBookmarkCount;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkCategoryService {

    private final RoomRepository roomRepository;
    private final BookmarkCategoryRepository bookmarkCategoryRepository;
    private final BookmarkRepository bookmarkRepository;

    @Transactional
    public BookmarkCategoryResult create(UUID roomId, BookmarkCategoryCreateCommand command) {
        Room room = getRoom(roomId);
        String name = command.name().trim();
        String colorCode = command.colorCode().trim();

        if (bookmarkCategoryRepository.existsByRoom_IdAndName(roomId, name)) {
            throw new CustomException(ErrorCode.BOOKMARK_CATEGORY_ALREADY_EXISTS);
        }

        BookmarkCategory category = BookmarkCategory.create(room, name, colorCode, null);
        try {
            return BookmarkCategoryResult.from(bookmarkCategoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.BOOKMARK_CATEGORY_ALREADY_EXISTS, e);
        }
    }

    public List<BookmarkCategoryResult> getCategories(UUID roomId) {
        getRoom(roomId);
        Map<Long, Long> countMap = bookmarkRepository.countGroupedByCategoryId(roomId)
                .stream()
                .collect(Collectors.toMap(CategoryBookmarkCount::categoryId, CategoryBookmarkCount::count));
        return bookmarkCategoryRepository.findAllByRoom_IdOrderByCreatedAtAsc(roomId).stream()
                .map(cat -> BookmarkCategoryResult.from(cat, countMap.getOrDefault(cat.getId(), 0L)))
                .toList();
    }

    @Transactional
    public BookmarkCategoryResult rename(UUID roomId, long categoryId, BookmarkCategoryRenameCommand command) {
        getRoom(roomId);
        BookmarkCategory category = getCategoryInRoom(roomId, categoryId);
        String name = command.name().trim();
        String colorCode = command.colorCode().trim();

        if (!category.getName().equals(name) && bookmarkCategoryRepository.existsByRoom_IdAndName(roomId, name)) {
            throw new CustomException(ErrorCode.BOOKMARK_CATEGORY_ALREADY_EXISTS);
        }

        category.update(name, colorCode);
        try {
            return BookmarkCategoryResult.from(bookmarkCategoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.BOOKMARK_CATEGORY_ALREADY_EXISTS, e);
        }
    }

    @Transactional
    public void delete(UUID roomId, long categoryId) {
        getRoom(roomId);
        BookmarkCategory category = getCategoryInRoom(roomId, categoryId);
        bookmarkRepository.deleteAllByCategory_Id(categoryId);
        bookmarkCategoryRepository.delete(category);
    }

    private Room getRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
    }

    private BookmarkCategory getCategoryInRoom(UUID roomId, long categoryId) {
        return bookmarkCategoryRepository.findByIdAndRoom_Id(categoryId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.BOOKMARK_CATEGORY_NOT_FOUND));
    }

}
