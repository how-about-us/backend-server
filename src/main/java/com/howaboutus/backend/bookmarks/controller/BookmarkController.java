package com.howaboutus.backend.bookmarks.controller;

import com.howaboutus.backend.bookmarks.controller.dto.BookmarkResponse;
import com.howaboutus.backend.bookmarks.controller.dto.CreateBookmarkRequest;
import com.howaboutus.backend.bookmarks.service.BookmarkService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms/{roomId}/bookmarks")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PostMapping
    public ResponseEntity<BookmarkResponse> create(
            @PathVariable UUID roomId,
            @RequestBody @Valid CreateBookmarkRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BookmarkResponse.from(bookmarkService.create(roomId, request.toCommand())));
    }

    @GetMapping
    public List<BookmarkResponse> getBookmarks(@PathVariable UUID roomId) {
        return bookmarkService.getBookmarks(roomId).stream()
                .map(BookmarkResponse::from)
                .toList();
    }

    @DeleteMapping("/{bookmarkId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID roomId,
            @PathVariable Long bookmarkId
    ) {
        bookmarkService.delete(roomId, bookmarkId);
        return ResponseEntity.noContent().build();
    }
}
