package com.howaboutus.backend.schedules.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.common.config.SecurityConfig;
import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import com.howaboutus.backend.schedules.service.ScheduleItemService;
import com.howaboutus.backend.schedules.service.dto.ScheduleItemCreateCommand;
import com.howaboutus.backend.schedules.service.dto.ScheduleItemResult;
import com.howaboutus.backend.schedules.service.dto.ScheduleItemUpdateCommand;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ScheduleItemController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ScheduleItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduleItemService scheduleItemService;

    @Test
    @DisplayName("googlePlaceId가 공백이면 400을 반환하고 서비스는 호출하지 않는다")
    void returnsBadRequestWhenGooglePlaceIdIsBlank() throws Exception {
        mockMvc.perform(post("/rooms/{roomId}/schedules/{scheduleId}/items", ROOM_ID, SCHEDULE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"googlePlaceId": "   ", "startTime": "09:30", "durationMinutes": 30}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("googlePlaceId는 공백일 수 없습니다"));

        verifyNoInteractions(scheduleItemService);
    }

    @Test
    @DisplayName("durationMinutes가 0 이하면 400을 반환하고 서비스는 호출하지 않는다")
    void returnsBadRequestWhenDurationMinutesIsNotPositive() throws Exception {
        mockMvc.perform(post("/rooms/{roomId}/schedules/{scheduleId}/items", ROOM_ID, SCHEDULE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"googlePlaceId": "place-1", "startTime": "09:30", "durationMinutes": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("durationMinutes는 1 이상이어야 합니다"));

        verifyNoInteractions(scheduleItemService);
    }

    @Test
    @DisplayName("일정 항목 생성 성공 시 201을 반환하고 명령값을 전달한다")
    void createsScheduleItemSuccessfully() throws Exception {
        given(scheduleItemService.create(eq(ROOM_ID), eq(SCHEDULE_ID), any(ScheduleItemCreateCommand.class)))
                .willReturn(SCHEDULE_ITEM_RESULT);

        mockMvc.perform(post("/rooms/{roomId}/schedules/{scheduleId}/items", ROOM_ID, SCHEDULE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"googlePlaceId": "place-1", "startTime": "09:30", "durationMinutes": 30}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemId").value(SCHEDULE_ITEM_RESULT.itemId()))
                .andExpect(jsonPath("$.scheduleId").value(SCHEDULE_ITEM_RESULT.scheduleId()))
                .andExpect(jsonPath("$.googlePlaceId").value(SCHEDULE_ITEM_RESULT.googlePlaceId()))
                .andExpect(jsonPath("$.startTime").value(SCHEDULE_ITEM_RESULT.startTime().toString()))
                .andExpect(jsonPath("$.durationMinutes").value(SCHEDULE_ITEM_RESULT.durationMinutes()))
                .andExpect(jsonPath("$.orderIndex").value(SCHEDULE_ITEM_RESULT.orderIndex()))
                .andExpect(jsonPath("$.createdAt").value(SCHEDULE_ITEM_RESULT.createdAt().toString()));

        ArgumentCaptor<ScheduleItemCreateCommand> captor = ArgumentCaptor.forClass(ScheduleItemCreateCommand.class);
        then(scheduleItemService).should().create(eq(ROOM_ID), eq(SCHEDULE_ID), captor.capture());
        assertThat(captor.getValue().googlePlaceId()).isEqualTo("place-1");
        assertThat(captor.getValue().startTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(captor.getValue().durationMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("일정 항목 수정 성공 시 200을 반환한다")
    void updatesScheduleItemSuccessfully() throws Exception {
        given(scheduleItemService.update(eq(ROOM_ID), eq(SCHEDULE_ID), eq(ITEM_ID), any(ScheduleItemUpdateCommand.class)))
                .willReturn(SCHEDULE_ITEM_RESULT);

        mockMvc.perform(patch("/rooms/{roomId}/schedules/{scheduleId}/items/{itemId}", ROOM_ID, SCHEDULE_ID, ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startTime": "10:30", "durationMinutes": 45}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value(SCHEDULE_ITEM_RESULT.itemId()))
                .andExpect(jsonPath("$.scheduleId").value(SCHEDULE_ITEM_RESULT.scheduleId()))
                .andExpect(jsonPath("$.googlePlaceId").value(SCHEDULE_ITEM_RESULT.googlePlaceId()))
                .andExpect(jsonPath("$.startTime").value(SCHEDULE_ITEM_RESULT.startTime().toString()))
                .andExpect(jsonPath("$.durationMinutes").value(SCHEDULE_ITEM_RESULT.durationMinutes()))
                .andExpect(jsonPath("$.orderIndex").value(SCHEDULE_ITEM_RESULT.orderIndex()))
                .andExpect(jsonPath("$.createdAt").value(SCHEDULE_ITEM_RESULT.createdAt().toString()));

        then(scheduleItemService).should().update(eq(ROOM_ID), eq(SCHEDULE_ID), eq(ITEM_ID), any(ScheduleItemUpdateCommand.class));
    }

    @Test
    @DisplayName("일정 항목 목록 조회 성공 시 배열을 반환한다")
    void returnsScheduleItemListSuccessfully() throws Exception {
        given(scheduleItemService.getItems(ROOM_ID, SCHEDULE_ID)).willReturn(List.of(SCHEDULE_ITEM_RESULT));

        mockMvc.perform(get("/rooms/{roomId}/schedules/{scheduleId}/items", ROOM_ID, SCHEDULE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].itemId").value(SCHEDULE_ITEM_RESULT.itemId()))
                .andExpect(jsonPath("$[0].scheduleId").value(SCHEDULE_ITEM_RESULT.scheduleId()))
                .andExpect(jsonPath("$[0].googlePlaceId").value(SCHEDULE_ITEM_RESULT.googlePlaceId()))
                .andExpect(jsonPath("$[0].startTime").value(SCHEDULE_ITEM_RESULT.startTime().toString()))
                .andExpect(jsonPath("$[0].durationMinutes").value(SCHEDULE_ITEM_RESULT.durationMinutes()))
                .andExpect(jsonPath("$[0].orderIndex").value(SCHEDULE_ITEM_RESULT.orderIndex()))
                .andExpect(jsonPath("$[0].createdAt").value(SCHEDULE_ITEM_RESULT.createdAt().toString()));
    }

    @Test
    @DisplayName("일정 항목 삭제 성공 시 204를 반환한다")
    void deletesScheduleItemSuccessfully() throws Exception {
        mockMvc.perform(delete("/rooms/{roomId}/schedules/{scheduleId}/items/{itemId}", ROOM_ID, SCHEDULE_ID, ITEM_ID))
                .andExpect(status().isNoContent());

        then(scheduleItemService).should().delete(ROOM_ID, SCHEDULE_ID, ITEM_ID);
    }

    private static final UUID ROOM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long SCHEDULE_ID = 10L;
    private static final Long ITEM_ID = 99L;
    private static final ScheduleItemResult SCHEDULE_ITEM_RESULT = new ScheduleItemResult(
            ITEM_ID,
            SCHEDULE_ID,
            "place-1",
            LocalTime.of(9, 30),
            30,
            0,
            Instant.parse("2025-01-01T00:00:00Z")
    );
}
