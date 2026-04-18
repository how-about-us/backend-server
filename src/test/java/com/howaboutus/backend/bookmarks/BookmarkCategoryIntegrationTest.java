package com.howaboutus.backend.bookmarks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.bookmarks.repository.BookmarkRepository;
import com.howaboutus.backend.bookmarks.repository.BookmarkCategoryRepository;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.support.BaseIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class BookmarkCategoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookmarkCategoryRepository bookmarkCategoryRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @AfterEach
    void tearDown() {
        bookmarkRepository.deleteAll();
        bookmarkCategoryRepository.deleteAll();
        roomRepository.deleteAll();
    }

    @Test
    @DisplayName("카테고리 생성, 목록 조회, 이름 변경, 삭제가 실제 HTTP 엔드포인트에서 동작한다")
    void bookmarkCategoryCrudWorksThroughHttpEndpoints() throws Exception {
        Room room = roomRepository.save(Room.create(
                "도쿄 여행",
                "도쿄",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 3),
                "TOKYO-CAT-HTTP-1",
                1L
        ));

        String createResponse = mockMvc.perform(post("/rooms/{roomId}/bookmark-categories", room.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "맛집"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(room.getId().toString()))
                .andExpect(jsonPath("$.name").value("맛집"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long categoryId = Long.valueOf(createResponse.replaceAll(".*\"categoryId\":(\\d+).*", "$1"));

        mockMvc.perform(get("/rooms/{roomId}/bookmark-categories", room.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryId").value(categoryId))
                .andExpect(jsonPath("$[0].name").value("맛집"));

        mockMvc.perform(patch("/rooms/{roomId}/bookmark-categories/{categoryId}", room.getId(), categoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "카페"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.name").value("카페"));

        mockMvc.perform(delete("/rooms/{roomId}/bookmark-categories/{categoryId}", room.getId(), categoryId))
                .andExpect(status().isNoContent());

        assertThat(bookmarkCategoryRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("다른 방의 카테고리를 수정하려고 하면 404를 반환한다")
    void returnsNotFoundWhenRenamingCategoryOutsideRoom() throws Exception {
        Room roomA = roomRepository.save(Room.create(
                "도쿄 여행",
                "도쿄",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 3),
                "TOKYO-CAT-HTTP-2",
                1L
        ));
        Room roomB = roomRepository.save(Room.create(
                "오사카 여행",
                "오사카",
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 3),
                "OSAKA-CAT-HTTP-2",
                2L
        ));

        String createResponse = mockMvc.perform(post("/rooms/{roomId}/bookmark-categories", roomA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "맛집"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long categoryId = Long.valueOf(createResponse.replaceAll(".*\"categoryId\":(\\d+).*", "$1"));

        mockMvc.perform(patch("/rooms/{roomId}/bookmark-categories/{categoryId}", roomB.getId(), categoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "카페"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.BOOKMARK_CATEGORY_NOT_FOUND.name()));
    }
}
