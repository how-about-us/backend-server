package com.howaboutus.backend.bookmarks.service.dto;

import com.howaboutus.backend.bookmarks.entity.BookmarkCategory;
import java.time.Instant;
import java.util.UUID;

public record BookmarkCategoryResult(
        Long categoryId,
        UUID roomId,
        String name,
        Long createdBy,
        Instant createdAt,
        long placeCount
) {

    public static BookmarkCategoryResult from(BookmarkCategory category) {
        return from(category, 0L);
    }

    public static BookmarkCategoryResult from(BookmarkCategory category, long placeCount) {
        return new BookmarkCategoryResult(
                category.getId(),
                category.getRoom().getId(),
                category.getName(),
                category.getCreatedBy(),
                category.getCreatedAt(),
                placeCount
        );
    }
}
