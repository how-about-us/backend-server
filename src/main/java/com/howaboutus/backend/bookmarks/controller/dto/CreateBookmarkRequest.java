package com.howaboutus.backend.bookmarks.controller.dto;

import com.howaboutus.backend.bookmarks.service.dto.BookmarkCreateCommand;
import jakarta.validation.constraints.NotBlank;

public record CreateBookmarkRequest(
        @NotBlank(message = "googlePlaceId는 공백일 수 없습니다")
        String googlePlaceId,
        String category
) {
    public BookmarkCreateCommand toCommand() {
        return new BookmarkCreateCommand(googlePlaceId, category);
    }
}
