package com.howaboutus.backend.schedules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.auth.service.JwtProvider;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.schedules.entity.Schedule;
import com.howaboutus.backend.schedules.entity.ScheduleItem;
import com.howaboutus.backend.schedules.repository.ScheduleItemRepository;
import com.howaboutus.backend.schedules.repository.ScheduleRepository;
import com.howaboutus.backend.support.BaseIntegrationTest;
import com.howaboutus.backend.user.entity.User;
import com.howaboutus.backend.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
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
class ScheduleIntegrationTest extends BaseIntegrationTest {

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
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleItemRepository scheduleItemRepository;

    @BeforeEach
    void setUp() {
        BDDMockito.given(jwtProvider.extractUserId(VALID_TOKEN)).willReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        scheduleItemRepository.deleteAll();
        scheduleRepository.deleteAll();
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("일정 생성 조회 삭제가 HTTP 흐름으로 동작한다")
    void scheduleFlowWorksEndToEnd() throws Exception {
        Room room = roomRepository.save(Room.create(
                "서울 여행",
                "서울",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 3),
                "SEOUL-SCH-1",
                1L
        ));
        authorizeRequestUserAsMember(room);

        String createResponse = mockMvc.perform(post("/rooms/{roomId}/schedules", room.getId())
                        .cookie(new Cookie("access_token", VALID_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dayNumber": 1,
                                  "date": "2026-05-01"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduleId").isNumber())
                .andExpect(jsonPath("$.roomId").value(room.getId().toString()))
                .andExpect(jsonPath("$.dayNumber").value(1))
                .andExpect(jsonPath("$.date").value("2026-05-01"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long scheduleId = ((Number) JsonPath.read(createResponse, "$.scheduleId")).longValue();
        String createdAt = JsonPath.read(createResponse, "$.createdAt");

        Optional<Schedule> savedSchedule = scheduleRepository.findAllByRoom_IdOrderByDayNumberAsc(room.getId())
                .stream()
                .filter(schedule -> schedule.getId().equals(scheduleId))
                .findFirst();

        assertThat(savedSchedule).isPresent();
        String persistedCreatedAt = savedSchedule.orElseThrow().getCreatedAt().toString();
        assertThat(createdAt).isEqualTo(persistedCreatedAt);

        mockMvc.perform(get("/rooms/{roomId}/schedules", room.getId())
                        .cookie(new Cookie("access_token", VALID_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].scheduleId").value(scheduleId))
                .andExpect(jsonPath("$[0].roomId").value(room.getId().toString()))
                .andExpect(jsonPath("$[0].dayNumber").value(1))
                .andExpect(jsonPath("$[0].date").value("2026-05-01"))
                .andExpect(jsonPath("$[0].createdAt").value(persistedCreatedAt));

        mockMvc.perform(delete("/rooms/{roomId}/schedules/{scheduleId}", room.getId(), scheduleId)
                        .cookie(new Cookie("access_token", VALID_TOKEN)))
                .andExpect(status().isNoContent());

        assertThat(scheduleRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("일정 항목 생성 후 목록 조회, 시간 수정, 삭제 시 순서 재정렬이 동작한다")
    void scheduleItemFlowWorksEndToEnd() throws Exception {
        Room room = roomRepository.save(Room.create(
                "도쿄 여행",
                "도쿄",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 3),
                "TOKYO-ITEM-INTEGRATION",
                1L
        ));
        authorizeRequestUserAsMember(room);

        Schedule schedule = scheduleRepository.saveAndFlush(Schedule.create(room, 1, LocalDate.of(2026, 5, 1)));

        mockMvc.perform(post("/rooms/{roomId}/schedules/{scheduleId}/items", room.getId(), schedule.getId())
                        .cookie(new Cookie("access_token", VALID_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"googlePlaceId": "place-1", "startTime": "09:00", "durationMinutes": 90}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderIndex").value(0));

        mockMvc.perform(post("/rooms/{roomId}/schedules/{scheduleId}/items", room.getId(), schedule.getId())
                        .cookie(new Cookie("access_token", VALID_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"googlePlaceId": "place-2"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderIndex").value(1));

        ScheduleItem firstItem = scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(schedule.getId()).getFirst();

        mockMvc.perform(patch("/rooms/{roomId}/schedules/{scheduleId}/items/{itemId}", room.getId(), schedule.getId(),
                        firstItem.getId())
                        .cookie(new Cookie("access_token", VALID_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"durationMinutes": 120}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("09:00"))
                .andExpect(jsonPath("$.durationMinutes").value(120));

        ScheduleItem updatedFirstItem = scheduleItemRepository.findById(firstItem.getId()).orElseThrow();
        assertThat(updatedFirstItem.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(updatedFirstItem.getDurationMinutes()).isEqualTo(120);

        mockMvc.perform(delete("/rooms/{roomId}/schedules/{scheduleId}/items/{itemId}", room.getId(), schedule.getId(),
                        firstItem.getId())
                        .cookie(new Cookie("access_token", VALID_TOKEN)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/rooms/{roomId}/schedules/{scheduleId}/items", room.getId(), schedule.getId())
                        .cookie(new Cookie("access_token", VALID_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderIndex").value(0))
                .andExpect(jsonPath("$[0].googlePlaceId").value("place-2"));
    }

    @Test
    @DisplayName("존재하지 않는 방에는 일정 API로 접근할 수 없다")
    void scheduleEndpointsRejectMissingRoom() throws Exception {
        UUID roomId = UUID.randomUUID();

        mockMvc.perform(post("/rooms/{roomId}/schedules", roomId)
                        .cookie(new Cookie("access_token", VALID_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dayNumber": 1,
                                  "date": "2026-06-01"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    private void authorizeRequestUserAsMember(Room room) {
        User user = userRepository.save(User.ofGoogle(
                "schedule-" + room.getId(),
                "schedule-" + room.getId() + "@test.com",
                "일정테스터",
                null
        ));
        BDDMockito.given(jwtProvider.extractUserId(VALID_TOKEN)).willReturn(user.getId());
        roomMemberRepository.saveAndFlush(RoomMember.of(room, user, RoomRole.MEMBER));
    }
}
