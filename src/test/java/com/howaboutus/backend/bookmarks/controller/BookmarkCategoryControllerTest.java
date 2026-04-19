package com.howaboutus.backend.bookmarks.controller;

import com.howaboutus.backend.bookmarks.service.BookmarkCategoryService;
import com.howaboutus.backend.bookmarks.service.dto.BookmarkCategoryResult;
import com.howaboutus.backend.common.config.SecurityConfig;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookmarkCategoryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class BookmarkCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookmarkCategoryService bookmarkCategoryService;

    @Test
    @DisplayName("카테고리 생성 성공 시 201을 반환한다")
    void createsBookmarkCategorySuccessfully() throws Exception {
        given(bookmarkCategoryService.create(eq(ROOM_ID), any()))
                .willReturn(BOOKMARK_CATEGORY_RESULT);

        mockMvc.perform(post("/rooms/{roomId}/bookmark-categories", ROOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "맛집", "colorCode": "#FF8800"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(BOOKMARK_CATEGORY_RESULT.categoryId()))
                .andExpect(jsonPath("$.roomId").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$.name").value(BOOKMARK_CATEGORY_RESULT.name()))
                .andExpect(jsonPath("$.colorCode").value(BOOKMARK_CATEGORY_RESULT.colorCode()))
                .andExpect(jsonPath("$.createdBy").value(BOOKMARK_CATEGORY_RESULT.createdBy()))
                .andExpect(jsonPath("$.createdAt").value(BOOKMARK_CATEGORY_RESULT.createdAt().toString()));
    }

    @Test
    @DisplayName("name이 공백이면 400을 반환한다")
    void returnsBadRequestWhenCreateNameIsBlank() throws Exception {
        mockMvc.perform(post("/rooms/{roomId}/bookmark-categories", ROOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "   ", "colorCode": "#FF8800"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("name은 공백일 수 없습니다"));

        verifyNoInteractions(bookmarkCategoryService);
    }

    @Test
    @DisplayName("name이 50자를 초과하면 400을 반환한다")
    void returnsBadRequestWhenCreateNameIsTooLong() throws Exception {
        mockMvc.perform(post("/rooms/{roomId}/bookmark-categories", ROOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "colorCode": "#FF8800"}
                                """.formatted("a".repeat(51))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("name은 50자 이하여야 합니다"));

        verifyNoInteractions(bookmarkCategoryService);
    }

    @Test
    @DisplayName("colorCode가 #RRGGBB 형식이 아니면 생성 시 400을 반환한다")
    void returnsBadRequestWhenCreateColorCodeIsInvalid() throws Exception {
        mockMvc.perform(post("/rooms/{roomId}/bookmark-categories", ROOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "맛집", "colorCode": "FF8800"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("colorCode는 #RRGGBB 형식이어야 합니다"));

        verifyNoInteractions(bookmarkCategoryService);
    }

    @Test
    @DisplayName("카테고리 목록 조회 성공 시 200을 반환한다")
    void returnsBookmarkCategoryListSuccessfully() throws Exception {
        given(bookmarkCategoryService.getCategories(ROOM_ID)).willReturn(List.of(BOOKMARK_CATEGORY_RESULT));

        mockMvc.perform(get("/rooms/{roomId}/bookmark-categories", ROOM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryId").value(BOOKMARK_CATEGORY_RESULT.categoryId()))
                .andExpect(jsonPath("$[0].roomId").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$[0].name").value(BOOKMARK_CATEGORY_RESULT.name()))
                .andExpect(jsonPath("$[0].colorCode").value(BOOKMARK_CATEGORY_RESULT.colorCode()))
                .andExpect(jsonPath("$[0].createdBy").value(BOOKMARK_CATEGORY_RESULT.createdBy()))
                .andExpect(jsonPath("$[0].createdAt").value(BOOKMARK_CATEGORY_RESULT.createdAt().toString()));
    }

    @Test
    @DisplayName("카테고리 이름 변경 성공 시 200을 반환한다")
    void renamesBookmarkCategorySuccessfully() throws Exception {
        given(bookmarkCategoryService.rename(eq(ROOM_ID), eq(CATEGORY_ID), any()))
                .willReturn(BOOKMARK_CATEGORY_RESULT);

        mockMvc.perform(patch("/rooms/{roomId}/bookmark-categories/{categoryId}", ROOM_ID, CATEGORY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "카페", "colorCode": "#3366FF"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(BOOKMARK_CATEGORY_RESULT.categoryId()))
                .andExpect(jsonPath("$.roomId").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$.name").value(BOOKMARK_CATEGORY_RESULT.name()))
                .andExpect(jsonPath("$.colorCode").value(BOOKMARK_CATEGORY_RESULT.colorCode()))
                .andExpect(jsonPath("$.createdBy").value(BOOKMARK_CATEGORY_RESULT.createdBy()))
                .andExpect(jsonPath("$.createdAt").value(BOOKMARK_CATEGORY_RESULT.createdAt().toString()));
    }

    @Test
    @DisplayName("name이 공백이면 수정 시 400을 반환한다")
    void returnsBadRequestWhenRenameNameIsBlank() throws Exception {
        mockMvc.perform(patch("/rooms/{roomId}/bookmark-categories/{categoryId}", ROOM_ID, CATEGORY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "   ", "colorCode": "#3366FF"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("name은 공백일 수 없습니다"));

        verifyNoInteractions(bookmarkCategoryService);
    }

    @Test
    @DisplayName("colorCode가 #RRGGBB 형식이 아니면 수정 시 400을 반환한다")
    void returnsBadRequestWhenRenameColorCodeIsInvalid() throws Exception {
        mockMvc.perform(patch("/rooms/{roomId}/bookmark-categories/{categoryId}", ROOM_ID, CATEGORY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "카페", "colorCode": "3366FF"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("colorCode는 #RRGGBB 형식이어야 합니다"));

        verifyNoInteractions(bookmarkCategoryService);
    }

    @Test
    @DisplayName("카테고리 삭제 성공 시 204를 반환한다")
    void deletesBookmarkCategorySuccessfully() throws Exception {
        mockMvc.perform(delete("/rooms/{roomId}/bookmark-categories/{categoryId}", ROOM_ID, CATEGORY_ID))
                .andExpect(status().isNoContent());

        then(bookmarkCategoryService).should().delete(ROOM_ID, CATEGORY_ID);
    }

    @Test
    @DisplayName("서비스가 BOOKMARK_CATEGORY_NOT_FOUND를 던지면 404를 반환한다")
    void returnsNotFoundWhenServiceThrowsCategoryNotFound() throws Exception {
        given(bookmarkCategoryService.create(eq(ROOM_ID), any()))
                .willThrow(new CustomException(ErrorCode.BOOKMARK_CATEGORY_NOT_FOUND));

        mockMvc.perform(post("/rooms/{roomId}/bookmark-categories", ROOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "맛집", "colorCode": "#FF8800"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKMARK_CATEGORY_NOT_FOUND"));
    }

    private static final UUID ROOM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long CATEGORY_ID = 10L;
    private static final BookmarkCategoryResult BOOKMARK_CATEGORY_RESULT = new BookmarkCategoryResult(
            CATEGORY_ID,
            ROOM_ID,
            "맛집",
            "#FF8800",
            7L,
            Instant.parse("2025-01-01T00:00:00Z"),
            3L
    );
}
