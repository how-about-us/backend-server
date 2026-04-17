package com.howaboutus.backend.bookmarks.controller.dto;

import com.howaboutus.backend.bookmarks.service.dto.BookmarkResult;
import java.time.Instant;
import java.util.UUID;

public record BookmarkResponse(
        Long bookmarkId,
        UUID roomId,
        String googlePlaceId,
        String category,
        Long addedBy,
        Instant createdAt
) {
    public static BookmarkResponse from(BookmarkResult result) {
        return new BookmarkResponse(
                result.bookmarkId(),
                result.roomId(),
                result.googlePlaceId(),
                result.category(),
                result.addedBy(),
                result.createdAt()
        );
    }
}
