package com.howaboutus.backend.bookmarks.controller.dto;

import com.howaboutus.backend.bookmarks.service.dto.BookmarkCategoryResult;
import java.time.Instant;
import java.util.UUID;

public record BookmarkCategoryResponse(
        Long categoryId,
        UUID roomId,
        String name,
        String colorCode,
        Long createdBy,
        Instant createdAt,
        long placeCount
) {
    public static BookmarkCategoryResponse from(BookmarkCategoryResult result) {
        return new BookmarkCategoryResponse(
                result.categoryId(),
                result.roomId(),
                result.name(),
                result.colorCode(),
                result.createdBy(),
                result.createdAt(),
                result.placeCount()
        );
    }
}
