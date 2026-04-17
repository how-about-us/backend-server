package com.howaboutus.backend.bookmarks.controller.dto;

import com.howaboutus.backend.bookmarks.service.dto.BookmarkCreateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBookmarkRequest(
        @NotBlank(message = "googlePlaceId는 공백일 수 없습니다")
        @Size(max = 300, message = "googlePlaceId는 300자 이하여야 합니다")
        String googlePlaceId,
        @Size(max = 30, message = "category는 30자 이하여야 합니다")
        String category
) {
    public BookmarkCreateCommand toCommand() {
        return new BookmarkCreateCommand(googlePlaceId, category);
    }
}
