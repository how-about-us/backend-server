package com.howaboutus.backend.schedules.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.howaboutus.backend.common.config.SecurityConfig;
import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import com.howaboutus.backend.schedules.service.ScheduleService;
import com.howaboutus.backend.schedules.service.dto.ScheduleCreateCommand;
import com.howaboutus.backend.schedules.service.dto.ScheduleResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ScheduleController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduleService scheduleService;

    @Test
    @DisplayName("dayNumber가 없으면 400을 반환하고 서비스는 호출하지 않는다")
    void returnsBadRequestWhenDayNumberIsMissing() throws Exception {
        mockMvc.perform(post("/rooms/{roomId}/schedules", ROOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date": "2025-01-02"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("dayNumber는 필수입니다"));

        verifyNoInteractions(scheduleService);
    }

    @Test
    @DisplayName("일정 생성 성공 시 201을 반환한다")
    void createsScheduleSuccessfully() throws Exception {
        given(scheduleService.create(eq(ROOM_ID), any(ScheduleCreateCommand.class))).willReturn(SCHEDULE_RESULT);

        mockMvc.perform(post("/rooms/{roomId}/schedules", ROOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dayNumber": 2, "date": "2025-01-02"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduleId").value(SCHEDULE_RESULT.scheduleId()))
                .andExpect(jsonPath("$.roomId").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$.dayNumber").value(SCHEDULE_RESULT.dayNumber()))
                .andExpect(jsonPath("$.date").value(SCHEDULE_RESULT.date().toString()))
                .andExpect(jsonPath("$.createdAt").value(SCHEDULE_RESULT.createdAt().toString()));

        then(scheduleService).should().create(eq(ROOM_ID), any(ScheduleCreateCommand.class));
    }

    @Test
    @DisplayName("일정 목록 조회 성공 시 배열을 반환한다")
    void returnsScheduleListSuccessfully() throws Exception {
        given(scheduleService.getSchedules(ROOM_ID)).willReturn(List.of(SCHEDULE_RESULT));

        mockMvc.perform(get("/rooms/{roomId}/schedules", ROOM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scheduleId").value(SCHEDULE_RESULT.scheduleId()))
                .andExpect(jsonPath("$[0].roomId").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$[0].dayNumber").value(SCHEDULE_RESULT.dayNumber()))
                .andExpect(jsonPath("$[0].date").value(SCHEDULE_RESULT.date().toString()))
                .andExpect(jsonPath("$[0].createdAt").value(SCHEDULE_RESULT.createdAt().toString()));
    }

    @Test
    @DisplayName("일정 삭제 성공 시 204를 반환한다")
    void deletesScheduleSuccessfully() throws Exception {
        mockMvc.perform(delete("/rooms/{roomId}/schedules/{scheduleId}", ROOM_ID, SCHEDULE_ID))
                .andExpect(status().isNoContent());

        then(scheduleService).should().delete(ROOM_ID, SCHEDULE_ID);
    }

    private static final UUID ROOM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long SCHEDULE_ID = 10L;
    private static final ScheduleResult SCHEDULE_RESULT = new ScheduleResult(
            SCHEDULE_ID,
            ROOM_ID,
            2,
            LocalDate.of(2025, 1, 2),
            Instant.parse("2025-01-01T00:00:00Z")
    );
}
