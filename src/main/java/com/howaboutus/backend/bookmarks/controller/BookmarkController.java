package com.howaboutus.backend.bookmarks.controller;

import com.howaboutus.backend.bookmarks.controller.dto.BookmarkResponse;
import com.howaboutus.backend.bookmarks.controller.dto.CreateBookmarkRequest;
import com.howaboutus.backend.bookmarks.controller.dto.UpdateBookmarkCategoryRequest;
import com.howaboutus.backend.bookmarks.service.BookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Bookmarks", description = "보관함 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms/{roomId}/bookmarks")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @Operation(
            summary = "보관함 항목 생성",
            description = "방에 후보 장소를 보관함 항목으로 추가합니다."
    )
    @PostMapping
    @SuppressWarnings("JvmTaintAnalysis")
    public ResponseEntity<BookmarkResponse> create(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "방 ID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID roomId,
            @RequestBody @Valid CreateBookmarkRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BookmarkResponse.from(bookmarkService.create(roomId, request.toCommand(), userId)));
    }

    @Operation(
            summary = "보관함 목록 조회",
            description = "방의 보관함 항목 목록을 카테고리별로 조회합니다."
    )
    @GetMapping
    public List<BookmarkResponse> getBookmarks(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "방 ID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID roomId,
            @Parameter(description = "카테고리 ID", example = "1")
            @RequestParam long categoryId
    ) {
        return bookmarkService.getBookmarks(roomId, categoryId, userId).stream()
                .map(BookmarkResponse::from)
                .toList();
    }

    @Operation(
            summary = "보관함 카테고리 변경",
            description = "보관함 항목의 카테고리를 현재 방 소속 카테고리로 변경합니다."
    )
    @PatchMapping("/{bookmarkId}/category")
    public BookmarkResponse updateCategory(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "방 ID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID roomId,
            @Parameter(description = "보관함 항목 ID", example = "1")
            @PathVariable long bookmarkId,
            @RequestBody @Valid UpdateBookmarkCategoryRequest request
    ) {
        return BookmarkResponse.from(
                bookmarkService.updateCategory(roomId, bookmarkId, request.categoryId(), userId)
        );
    }

    @Operation(
            summary = "보관함 항목 삭제",
            description = "방의 보관함 항목을 삭제합니다."
    )
    @DeleteMapping("/{bookmarkId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "방 ID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID roomId,
            @Parameter(description = "보관함 항목 ID", example = "1")
            @PathVariable long bookmarkId
    ) {
        bookmarkService.delete(roomId, bookmarkId, userId);
        return ResponseEntity.noContent().build();
    }
}
