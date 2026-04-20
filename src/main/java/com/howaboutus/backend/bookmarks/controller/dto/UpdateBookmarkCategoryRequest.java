package com.howaboutus.backend.bookmarks.controller.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateBookmarkCategoryRequest(
        @NotNull(message = "categoryId는 필수입니다")
        Long categoryId
) {
}
