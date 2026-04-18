package com.howaboutus.backend.bookmarks.controller.dto;

import com.howaboutus.backend.bookmarks.service.dto.BookmarkCategoryRenameCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameBookmarkCategoryRequest(
        @NotBlank(message = "name은 공백일 수 없습니다")
        @Size(max = 50, message = "name은 50자 이하여야 합니다")
        String name
) {
    public BookmarkCategoryRenameCommand toCommand() {
        return new BookmarkCategoryRenameCommand(name);
    }
}
