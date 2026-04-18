package com.howaboutus.backend.bookmarks.controller.dto;

import com.howaboutus.backend.bookmarks.service.dto.BookmarkCategoryCreateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateBookmarkCategoryRequest(
        @NotBlank(message = "name은 공백일 수 없습니다")
        @Size(max = 50, message = "name은 50자 이하여야 합니다")
        @Pattern(regexp = "[\\p{L}\\p{N} _\\-]+", message = "name에 허용되지 않는 문자가 포함되어 있습니다")
        String name
) {
    public BookmarkCategoryCreateCommand toCommand() {
        return new BookmarkCategoryCreateCommand(name);
    }
}
