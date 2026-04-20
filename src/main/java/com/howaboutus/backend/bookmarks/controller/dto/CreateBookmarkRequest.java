package com.howaboutus.backend.bookmarks.controller.dto;

import com.howaboutus.backend.bookmarks.service.dto.BookmarkCreateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateBookmarkRequest(
        @NotBlank(message = "googlePlaceId는 공백일 수 없습니다")
        @Size(max = 300, message = "googlePlaceId는 300자 이하여야 합니다")
        @Pattern(regexp = "[A-Za-z0-9_\\-:]+", message = "googlePlaceId 형식이 올바르지 않습니다")
        String googlePlaceId,
        @NotNull(message = "categoryId는 필수입니다")
        Long categoryId
) {
    public BookmarkCreateCommand toCommand() {
        return new BookmarkCreateCommand(googlePlaceId, categoryId);
    }
}
