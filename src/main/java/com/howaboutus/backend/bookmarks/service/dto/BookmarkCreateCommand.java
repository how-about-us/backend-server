package com.howaboutus.backend.bookmarks.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookmarkCreateCommand(
        @NotBlank(message = "googlePlaceId는 공백일 수 없습니다")
        @Size(max = 300, message = "googlePlaceId는 300자 이하여야 합니다")
        String googlePlaceId,
        @NotNull(message = "categoryId는 필수입니다")
        Long categoryId
) {
}
