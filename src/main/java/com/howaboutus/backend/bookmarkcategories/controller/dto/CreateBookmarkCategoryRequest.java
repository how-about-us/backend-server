package com.howaboutus.backend.bookmarkcategories.controller.dto;

import com.howaboutus.backend.bookmarkcategories.service.dto.BookmarkCategoryCreateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBookmarkCategoryRequest(
        @NotBlank(message = "name은 공백일 수 없습니다")
        @Size(max = 50, message = "name은 50자 이하여야 합니다")
        String name
) {
    public BookmarkCategoryCreateCommand toCommand() {
        return new BookmarkCategoryCreateCommand(name);
    }
}
