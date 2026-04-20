package com.howaboutus.backend.schedules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.schedules.entity.Schedule;
import com.howaboutus.backend.schedules.repository.ScheduleRepository;
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
class ScheduleIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @AfterEach
    void tearDown() {
        scheduleRepository.deleteAll();
        roomRepository.deleteAll();
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

        mockMvc.perform(post("/rooms/{roomId}/schedules", room.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dayNumber": 1,
                                  "date": "2026-05-01"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(room.getId().toString()))
                .andExpect(jsonPath("$.dayNumber").value(1))
                .andExpect(jsonPath("$.date").value("2026-05-01"));

        Schedule schedule = scheduleRepository.findAll().getFirst();

        mockMvc.perform(get("/rooms/{roomId}/schedules", room.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scheduleId").value(schedule.getId()))
                .andExpect(jsonPath("$[0].roomId").value(room.getId().toString()))
                .andExpect(jsonPath("$[0].dayNumber").value(1))
                .andExpect(jsonPath("$[0].date").value("2026-05-01"));

        mockMvc.perform(delete("/rooms/{roomId}/schedules/{scheduleId}", room.getId(), schedule.getId()))
                .andExpect(status().isNoContent());

        assertThat(scheduleRepository.findAll()).isEmpty();
    }
}
