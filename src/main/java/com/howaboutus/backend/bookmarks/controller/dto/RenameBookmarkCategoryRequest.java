package com.howaboutus.backend.bookmarks.controller.dto;

import com.howaboutus.backend.bookmarks.service.dto.BookmarkCategoryRenameCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RenameBookmarkCategoryRequest(
        @NotBlank(message = "name은 공백일 수 없습니다")
        @Size(max = 50, message = "name은 50자 이하여야 합니다")
        String name,
        @NotBlank(message = "colorCode는 공백일 수 없습니다")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "colorCode는 #RRGGBB 형식이어야 합니다")
        String colorCode
) {
    public BookmarkCategoryRenameCommand toCommand() {
        return new BookmarkCategoryRenameCommand(name, colorCode);
    }
}
