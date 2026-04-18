package com.howaboutus.backend.bookmarks.controller.dto;

import com.howaboutus.backend.bookmarks.service.dto.BookmarkCategoryResult;
import java.time.Instant;
import java.util.UUID;

public record BookmarkCategoryResponse(
        Long categoryId,
        UUID roomId,
        String name,
        Long createdBy,
        Instant createdAt
) {
    public static BookmarkCategoryResponse from(BookmarkCategoryResult result) {
        return new BookmarkCategoryResponse(
                result.categoryId(),
                result.roomId(),
                result.name(),
                result.createdBy(),
                result.createdAt()
        );
    }
}
