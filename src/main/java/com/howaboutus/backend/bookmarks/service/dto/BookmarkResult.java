package com.howaboutus.backend.bookmarks.service.dto;

import com.howaboutus.backend.bookmarks.entity.Bookmark;
import java.time.Instant;
import java.util.UUID;

public record BookmarkResult(
        Long bookmarkId,
        UUID roomId,
        String googlePlaceId,
        String category,
        Long addedBy,
        Instant createdAt
) {
    public static BookmarkResult from(Bookmark bookmark) {
        return new BookmarkResult(
                bookmark.getId(),
                bookmark.getRoom().getId(),
                bookmark.getGooglePlaceId(),
                bookmark.getCategory(),
                bookmark.getAddedBy(),
                bookmark.getCreatedAt()
        );
    }
}
