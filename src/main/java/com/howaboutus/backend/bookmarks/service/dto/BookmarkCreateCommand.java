package com.howaboutus.backend.bookmarks.service.dto;

public record BookmarkCreateCommand(
        String googlePlaceId,
        Long categoryId
) {
}
