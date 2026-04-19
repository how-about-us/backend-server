package com.howaboutus.backend.bookmarks.controller;

import com.howaboutus.backend.bookmarks.controller.dto.BookmarkCategoryResponse;
import com.howaboutus.backend.bookmarks.controller.dto.CreateBookmarkCategoryRequest;
import com.howaboutus.backend.bookmarks.controller.dto.RenameBookmarkCategoryRequest;
import com.howaboutus.backend.bookmarks.service.BookmarkCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Bookmark Categories", description = "보관함 카테고리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms/{roomId}/bookmark-categories")
public class BookmarkCategoryController {

    private final BookmarkCategoryService bookmarkCategoryService;

    @Operation(
            summary = "보관함 카테고리 생성",
            description = "방에서 사용할 사용자 정의 보관함 카테고리를 생성합니다."
    )
    @PostMapping
    @SuppressWarnings("JvmTaintAnalysis")
    public ResponseEntity<BookmarkCategoryResponse> create(
            @Parameter(description = "방 ID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID roomId,
            @RequestBody @Valid CreateBookmarkCategoryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BookmarkCategoryResponse.from(
                        bookmarkCategoryService.create(roomId, request.toCommand())
                ));
    }

    @Operation(
            summary = "보관함 카테고리 목록 조회",
            description = "방에서 사용 가능한 보관함 카테고리 목록을 조회합니다."
    )
    @GetMapping
    public List<BookmarkCategoryResponse> getCategories(
            @Parameter(description = "방 ID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID roomId
    ) {
        return bookmarkCategoryService.getCategories(roomId).stream()
                .map(BookmarkCategoryResponse::from)
                .toList();
    }

    @Operation(
            summary = "보관함 카테고리 수정",
            description = "기존 보관함 카테고리의 이름과 색상 코드를 수정합니다."
    )
    @PatchMapping("/{categoryId}")
    public BookmarkCategoryResponse rename(
            @Parameter(description = "방 ID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID roomId,
            @Parameter(description = "보관함 카테고리 ID", example = "1")
            @PathVariable Long categoryId,
            @RequestBody @Valid RenameBookmarkCategoryRequest request
    ) {
        return BookmarkCategoryResponse.from(
                bookmarkCategoryService.rename(roomId, categoryId, request.toCommand())
        );
    }

    @Operation(
            summary = "보관함 카테고리 삭제",
            description = "보관함 카테고리를 삭제합니다. 소속 보관함 항목도 함께 삭제됩니다."
    )
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "방 ID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID roomId,
            @Parameter(description = "보관함 카테고리 ID", example = "1")
            @PathVariable Long categoryId
    ) {
        bookmarkCategoryService.delete(roomId, categoryId);
        return ResponseEntity.noContent().build();
    }
}
