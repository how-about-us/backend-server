package com.howaboutus.backend.bookmarks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.bookmarks.entity.Bookmark;
import com.howaboutus.backend.bookmarks.entity.BookmarkCategory;
import com.howaboutus.backend.bookmarks.repository.BookmarkCategoryRepository;
import com.howaboutus.backend.bookmarks.repository.BookmarkRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class BookmarkIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private BookmarkCategoryRepository bookmarkCategoryRepository;

    @AfterEach
    void tearDown() {
        bookmarkRepository.deleteAll();
        bookmarkCategoryRepository.deleteAll();
        roomRepository.deleteAll();
    }

    @Test
    @DisplayName("북마크는 실제 카테고리와 연결되고, 카테고리 변경과 삭제가 동작한다")
    void bookmarkCategoryFlowWorksEndToEnd() throws Exception {
        Room room = roomRepository.save(Room.create(
                "도쿄 여행",
                "도쿄",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 3),
                "TOKYO-BMK-1",
                1L
        ));
        BookmarkCategory foodCategory = bookmarkCategoryRepository.saveAndFlush(
                BookmarkCategory.create(room, "맛집", "#FF8800", null)
        );
        BookmarkCategory cafeCategory = bookmarkCategoryRepository.saveAndFlush(
                BookmarkCategory.create(room, "카페", "#3366FF", null)
        );

        mockMvc.perform(post("/rooms/{roomId}/bookmarks", room.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"googlePlaceId": "place-1", "categoryId": %s}
                                """.formatted(foodCategory.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(foodCategory.getId()))
                .andExpect(jsonPath("$.category").value("맛집"));

        Bookmark bookmark = bookmarkRepository.findAllByRoom_IdOrderByCreatedAtDesc(room.getId()).getFirst();

        mockMvc.perform(patch("/rooms/{roomId}/bookmarks/{bookmarkId}/category", room.getId(), bookmark.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %s}
                                """.formatted(cafeCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookmarkId").value(bookmark.getId()))
                .andExpect(jsonPath("$.categoryId").value(cafeCategory.getId()))
                .andExpect(jsonPath("$.category").value("카페"));

        mockMvc.perform(get("/rooms/{roomId}/bookmarks", room.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryId").value(cafeCategory.getId()))
                .andExpect(jsonPath("$[0].category").value("카페"));

        mockMvc.perform(post("/rooms/{roomId}/bookmarks", room.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"googlePlaceId": "place-2", "categoryId": %s}
                                """.formatted(cafeCategory.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/rooms/{roomId}/bookmark-categories/{categoryId}", room.getId(), cafeCategory.getId()))
                .andExpect(status().isNoContent());

        assertThat(bookmarkRepository.findAllByRoom_IdOrderByCreatedAtDesc(room.getId())).isEmpty();
    }

    @Test
    @DisplayName("같은 방에서는 카테고리 이름이 중복될 수 없지만 다른 방에서는 허용된다")
    void categoryNameMustBeUniquePerRoom() {
        Room roomA = roomRepository.save(Room.create(
                "오사카 여행",
                "오사카",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                "OSAKA-CAT-1",
                2L
        ));
        Room roomB = roomRepository.save(Room.create(
                "후쿠오카 여행",
                "후쿠오카",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 2),
                "FUKUOKA-CAT-1",
                3L
        ));

        bookmarkCategoryRepository.saveAndFlush(BookmarkCategory.create(roomA, "맛집", "#FF8800", null));
        bookmarkCategoryRepository.saveAndFlush(BookmarkCategory.create(roomB, "맛집", "#FF8800", null));

        assertThatThrownBy(() ->
                bookmarkCategoryRepository.saveAndFlush(BookmarkCategory.create(roomA, "맛집", "#3366FF", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("북마크는 다른 방의 카테고리를 참조할 수 없다")
    void bookmarkCannotReferenceCategoryFromAnotherRoom() {
        Room roomA = roomRepository.save(Room.create(
                "도쿄 여행",
                "도쿄",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 3),
                "TOKYO-CAT-2",
                1L
        ));
        Room roomB = roomRepository.save(Room.create(
                "오사카 여행",
                "오사카",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 3),
                "OSAKA-CAT-2",
                2L
        ));
        BookmarkCategory category = bookmarkCategoryRepository.saveAndFlush(
                BookmarkCategory.create(roomA, "맛집", "#FF8800", null)
        );

        assertThatThrownBy(() ->
                bookmarkRepository.saveAndFlush(Bookmark.create(roomB, "place-cross-room", category, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
