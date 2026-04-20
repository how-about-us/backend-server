package com.howaboutus.backend.bookmarks.controller.dto;

import com.howaboutus.backend.bookmarks.service.dto.BookmarkCategoryCreateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateBookmarkCategoryRequest(
        @BookmarkCategoryName
        String name,
        @NotBlank(message = "colorCode는 공백일 수 없습니다")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "colorCode는 #RRGGBB 형식이어야 합니다")
        String colorCode
) {
    public BookmarkCategoryCreateCommand toCommand() {
        return new BookmarkCategoryCreateCommand(name, colorCode);
    }
}
