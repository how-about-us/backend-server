package com.howaboutus.backend.bookmarks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.bookmarks.entity.Bookmark;
import com.howaboutus.backend.bookmarks.repository.BookmarkRepository;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.support.BaseIntegrationTest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class BookmarkIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @AfterEach
    void tearDown() {
        bookmarkRepository.deleteAll();
        roomRepository.deleteAll();
    }

    @Test
    @DisplayName("보관함 생성, 조회, 삭제를 실제 HTTP 엔드포인트로 검증한다")
    void createsListsAndDeletesBookmarkThroughHttpEndpoints() throws Exception {
        Room targetRoom = roomRepository.save(Room.create(
                "도쿄 여행",
                "도쿄",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 3),
                "TOKYO2026",
                1L
        ));
        Room otherRoom = roomRepository.save(Room.create(
                "오사카 여행",
                "오사카",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                "OSAKA2026",
                2L
        ));

        bookmarkRepository.saveAndFlush(Bookmark.create(targetRoom, "place-old", "CAFE", null));
        bookmarkRepository.saveAndFlush(Bookmark.create(targetRoom, "place-middle", "RESTAURANT", null));
        bookmarkRepository.saveAndFlush(Bookmark.create(otherRoom, "place-foreign", "HOTEL", null));

        mockMvc.perform(post("/rooms/{roomId}/bookmarks", targetRoom.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"googlePlaceId": "place-new", "category": "CAFE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(targetRoom.getId().toString()))
                .andExpect(jsonPath("$.googlePlaceId").value("place-new"))
                .andExpect(jsonPath("$.category").value("CAFE"))
                .andExpect(jsonPath("$.addedBy").value(nullValue()));

        Bookmark savedBookmark = bookmarkRepository.findAllByRoom_IdOrderByCreatedAtDesc(targetRoom.getId()).getFirst();
        Long bookmarkId = savedBookmark.getId();

        assertThat(bookmarkRepository.findAllByRoom_IdOrderByCreatedAtDesc(targetRoom.getId()))
                .hasSize(3);
        assertThat(savedBookmark.getRoom().getId()).isEqualTo(targetRoom.getId());
        assertThat(savedBookmark.getGooglePlaceId()).isEqualTo("place-new");
        assertThat(savedBookmark.getCategory()).isEqualTo("CAFE");
        assertThat(savedBookmark.getAddedBy()).isNull();

        mockMvc.perform(get("/rooms/{roomId}/bookmarks", targetRoom.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].bookmarkId").value(bookmarkId))
                .andExpect(jsonPath("$[0].roomId").value(targetRoom.getId().toString()))
                .andExpect(jsonPath("$[0].googlePlaceId").value("place-new"))
                .andExpect(jsonPath("$[1].roomId").value(targetRoom.getId().toString()))
                .andExpect(jsonPath("$[1].googlePlaceId").value("place-middle"))
                .andExpect(jsonPath("$[2].roomId").value(targetRoom.getId().toString()))
                .andExpect(jsonPath("$[2].googlePlaceId").value("place-old"))
                .andExpect(jsonPath("$[*].googlePlaceId").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("place-foreign"))))
                .andExpect(jsonPath("$[0].category").value("CAFE"))
                .andExpect(jsonPath("$[0].addedBy").value(nullValue()));

        mockMvc.perform(get("/rooms/{roomId}/bookmarks", otherRoom.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].roomId").value(otherRoom.getId().toString()))
                .andExpect(jsonPath("$[0].googlePlaceId").value("place-foreign"))
                .andExpect(jsonPath("$[0].category").value("HOTEL"))
                .andExpect(jsonPath("$[0].addedBy").value(nullValue()));

        mockMvc.perform(delete("/rooms/{roomId}/bookmarks/{bookmarkId}", targetRoom.getId(), bookmarkId))
                .andExpect(status().isNoContent());

        assertThat(bookmarkRepository.findAllByRoom_IdOrderByCreatedAtDesc(targetRoom.getId()))
                .extracting(Bookmark::getGooglePlaceId)
                .containsExactly("place-middle", "place-old");
    }

    @Test
    @DisplayName("같은 방에 같은 장소를 두 번 추가하면 409를 반환한다")
    void returnsConflictWhenCreatingDuplicateBookmark() throws Exception {
        Room room = roomRepository.save(Room.create(
                "오사카 여행",
                "오사카",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                "OSAKA2026",
                2L
        ));

        mockMvc.perform(post("/rooms/{roomId}/bookmarks", room.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"googlePlaceId": "place-1", "category": "CAFE"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/rooms/{roomId}/bookmarks", room.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"googlePlaceId": "place-1", "category": "RESTAURANT"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.BOOKMARK_ALREADY_EXISTS.name()));
    }

    @Test
    @DisplayName("없는 방에 북마크를 추가하면 404를 반환한다")
    void returnsNotFoundWhenCreatingBookmarkForMissingRoom() throws Exception {
        UUID missingRoomId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        mockMvc.perform(post("/rooms/{roomId}/bookmarks", missingRoomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"googlePlaceId": "place-1", "category": "CAFE"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ROOM_NOT_FOUND.name()));
    }
}
