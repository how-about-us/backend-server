package com.howaboutus.backend.bookmarks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.auth.service.JwtProvider;
import com.howaboutus.backend.bookmarks.repository.BookmarkRepository;
import com.howaboutus.backend.bookmarks.repository.BookmarkCategoryRepository;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.support.BaseIntegrationTest;
import com.howaboutus.backend.user.entity.User;
import com.howaboutus.backend.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class BookmarkCategoryIntegrationTest extends BaseIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final String VALID_TOKEN = "valid-jwt";

    @MockitoBean
    private JwtProvider jwtProvider;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomMemberRepository roomMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookmarkCategoryRepository bookmarkCategoryRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @BeforeEach
    void setUp() {
        BDDMockito.given(jwtProvider.extractUserId(VALID_TOKEN)).willReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        bookmarkRepository.deleteAll();
        bookmarkCategoryRepository.deleteAll();
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
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
        authorizeRequestUserAsMember(room);

        String createResponse = mockMvc.perform(post("/rooms/{roomId}/bookmark-categories", room.getId())
                        .cookie(new Cookie("access_token", VALID_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "맛집", "colorCode": "#FF8800"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(room.getId().toString()))
                .andExpect(jsonPath("$.name").value("맛집"))
                .andExpect(jsonPath("$.colorCode").value("#FF8800"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long categoryId = ((Number) JsonPath.read(createResponse, "$.categoryId")).longValue();

        mockMvc.perform(get("/rooms/{roomId}/bookmark-categories", room.getId())
                        .cookie(new Cookie("access_token", VALID_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryId").value(categoryId))
                .andExpect(jsonPath("$[0].name").value("맛집"))
                .andExpect(jsonPath("$[0].colorCode").value("#FF8800"));

        mockMvc.perform(patch("/rooms/{roomId}/bookmark-categories/{categoryId}", room.getId(), categoryId)
                        .cookie(new Cookie("access_token", VALID_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "카페", "colorCode": "#3366FF"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.name").value("카페"))
                .andExpect(jsonPath("$.colorCode").value("#3366FF"));

        mockMvc.perform(delete("/rooms/{roomId}/bookmark-categories/{categoryId}", room.getId(), categoryId)
                        .cookie(new Cookie("access_token", VALID_TOKEN)))
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
        authorizeRequestUserAsMember(roomA, roomB);

        String createResponse = mockMvc.perform(post("/rooms/{roomId}/bookmark-categories", roomA.getId())
                        .cookie(new Cookie("access_token", VALID_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "맛집", "colorCode": "#FF8800"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long categoryId = ((Number) JsonPath.read(createResponse, "$.categoryId")).longValue();

        mockMvc.perform(patch("/rooms/{roomId}/bookmark-categories/{categoryId}", roomB.getId(), categoryId)
                        .cookie(new Cookie("access_token", VALID_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "카페", "colorCode": "#3366FF"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.BOOKMARK_CATEGORY_NOT_FOUND.name()));
    }

    private void authorizeRequestUserAsMember(Room... rooms) {
        User user = userRepository.save(User.ofGoogle(
                "bookmark-category-" + rooms[0].getId(),
                "bookmark-category-" + rooms[0].getId() + "@test.com",
                "카테고리테스터",
                null
        ));
        BDDMockito.given(jwtProvider.extractUserId(VALID_TOKEN)).willReturn(user.getId());
        for (Room room : rooms) {
            roomMemberRepository.save(RoomMember.of(room, user, RoomRole.MEMBER));
        }
        roomMemberRepository.flush();
    }
}
