package com.howaboutus.backend.bookmarks.controller;

import com.howaboutus.backend.bookmarks.service.BookmarkService;
import com.howaboutus.backend.bookmarks.service.dto.BookmarkResult;
import com.howaboutus.backend.common.config.SecurityConfig;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import com.howaboutus.backend.bookmarks.service.dto.BookmarkCreateCommand;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookmarkController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class BookmarkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookmarkService bookmarkService;

    @Test
    @DisplayName("googlePlaceId가 공백이면 400을 반환한다")
    void returnsBadRequestWhenGooglePlaceIdIsBlank() throws Exception {
        mockMvc.perform(post("/rooms/{roomId}/bookmarks", ROOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"googlePlaceId": "   ", "category": "CAFE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("googlePlaceId는 공백일 수 없습니다"));

        verifyNoInteractions(bookmarkService);
    }

    @Test
    @DisplayName("북마크 생성 성공 시 201을 반환한다")
    void createsBookmarkSuccessfully() throws Exception {
        given(bookmarkService.create(eq(ROOM_ID), any(BookmarkCreateCommand.class))).willReturn(BOOKMARK_RESULT);

        mockMvc.perform(post("/rooms/{roomId}/bookmarks", ROOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"googlePlaceId": "place-1", "category": "CAFE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookmarkId").value(BOOKMARK_RESULT.bookmarkId()))
                .andExpect(jsonPath("$.roomId").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$.googlePlaceId").value("place-1"))
                .andExpect(jsonPath("$.category").value("CAFE"))
                .andExpect(jsonPath("$.addedBy").value(BOOKMARK_RESULT.addedBy()))
                .andExpect(jsonPath("$.createdAt").value(BOOKMARK_RESULT.createdAt().toString()));

        ArgumentCaptor<BookmarkCreateCommand> captor = ArgumentCaptor.forClass(BookmarkCreateCommand.class);
        then(bookmarkService).should().create(eq(ROOM_ID), captor.capture());
        assertThat(captor.getValue().googlePlaceId()).isEqualTo("place-1");
        assertThat(captor.getValue().category()).isEqualTo("CAFE");
    }

    @Test
    @DisplayName("방이 없으면 목록 조회 시 404를 반환한다")
    void returnsNotFoundWhenRoomIsMissing() throws Exception {
        given(bookmarkService.getBookmarks(ROOM_ID))
                .willThrow(new CustomException(ErrorCode.ROOM_NOT_FOUND));

        mockMvc.perform(get("/rooms/{roomId}/bookmarks", ROOM_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    @DisplayName("북마크 삭제 성공 시 204를 반환한다")
    void deletesBookmarkSuccessfully() throws Exception {
        mockMvc.perform(delete("/rooms/{roomId}/bookmarks/{bookmarkId}", ROOM_ID, BOOKMARK_ID))
                .andExpect(status().isNoContent());

        then(bookmarkService).should().delete(ROOM_ID, BOOKMARK_ID);
    }

    private static final UUID ROOM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long BOOKMARK_ID = 10L;
    private static final BookmarkResult BOOKMARK_RESULT = new BookmarkResult(
            BOOKMARK_ID,
            ROOM_ID,
            "place-1",
            "CAFE",
            7L,
            Instant.parse("2025-01-01T00:00:00Z")
    );
}
