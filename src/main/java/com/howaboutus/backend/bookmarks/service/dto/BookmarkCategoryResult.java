package com.howaboutus.backend.bookmarks.service.dto;

import com.howaboutus.backend.bookmarks.entity.BookmarkCategory;
import java.time.Instant;
import java.util.UUID;

public record BookmarkCategoryResult(
        long categoryId,
        UUID roomId,
        String name,
        String colorCode,
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
                category.getColorCode(),
                category.getCreatedBy(),
                category.getCreatedAt(),
                placeCount
        );
    }
}
