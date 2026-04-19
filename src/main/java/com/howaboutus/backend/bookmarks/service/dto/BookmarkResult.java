package com.howaboutus.backend.bookmarks.service.dto;

import com.howaboutus.backend.bookmarks.entity.Bookmark;
import java.time.Instant;
import java.util.UUID;

public record BookmarkResult(
        long bookmarkId,
        UUID roomId,
        String googlePlaceId,
        long categoryId,
        String category,
        Long addedBy,
        Instant createdAt
) {
    public static BookmarkResult from(Bookmark bookmark) {
        return new BookmarkResult(
                bookmark.getId(),
                bookmark.getRoom().getId(),
                bookmark.getGooglePlaceId(),
                bookmark.getCategory().getId(),
                bookmark.getCategoryName(),
                bookmark.getAddedBy(),
                bookmark.getCreatedAt()
        );
    }
}
