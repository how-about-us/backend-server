package com.howaboutus.backend.bookmarks.controller;

import com.howaboutus.backend.bookmarks.controller.dto.BookmarkCategoryResponse;
import com.howaboutus.backend.bookmarks.controller.dto.CreateBookmarkCategoryRequest;
import com.howaboutus.backend.bookmarks.controller.dto.RenameBookmarkCategoryRequest;
import com.howaboutus.backend.bookmarks.service.BookmarkCategoryService;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms/{roomId}/bookmark-categories")
public class BookmarkCategoryController {

    private final BookmarkCategoryService bookmarkCategoryService;

    @PostMapping
    @SuppressWarnings("JvmTaintAnalysis")
    public ResponseEntity<BookmarkCategoryResponse> create(
            @PathVariable UUID roomId,
            @RequestBody @Valid CreateBookmarkCategoryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BookmarkCategoryResponse.from(
                        bookmarkCategoryService.create(roomId, request.toCommand())
                ));
    }

    @GetMapping
    public List<BookmarkCategoryResponse> getCategories(@PathVariable UUID roomId) {
        return bookmarkCategoryService.getCategories(roomId).stream()
                .map(BookmarkCategoryResponse::from)
                .toList();
    }

    @PatchMapping("/{categoryId}")
    public BookmarkCategoryResponse rename(
            @PathVariable UUID roomId,
            @PathVariable Long categoryId,
            @RequestBody @Valid RenameBookmarkCategoryRequest request
    ) {
        return BookmarkCategoryResponse.from(
                bookmarkCategoryService.rename(roomId, categoryId, request.toCommand())
        );
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID roomId,
            @PathVariable Long categoryId
    ) {
        bookmarkCategoryService.delete(roomId, categoryId);
        return ResponseEntity.noContent().build();
    }
}
