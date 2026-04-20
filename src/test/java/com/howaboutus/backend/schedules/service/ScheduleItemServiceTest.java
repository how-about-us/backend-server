package com.howaboutus.backend.schedules.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.schedules.entity.Schedule;
import com.howaboutus.backend.schedules.entity.ScheduleItem;
import com.howaboutus.backend.schedules.repository.ScheduleItemRepository;
import com.howaboutus.backend.schedules.repository.ScheduleRepository;
import com.howaboutus.backend.schedules.service.dto.ScheduleItemCreateCommand;
import com.howaboutus.backend.schedules.service.dto.ScheduleItemResult;
import com.howaboutus.backend.schedules.service.dto.ScheduleItemUpdateCommand;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ScheduleItemServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleItemRepository scheduleItemRepository;

    private ScheduleItemService scheduleItemService;

    @BeforeEach
    void setUp() {
        scheduleItemService = new ScheduleItemService(roomRepository, scheduleRepository, scheduleItemRepository);
    }

    @Test
    @DisplayName("일정 항목 생성 시 같은 일정의 다음 orderIndex를 할당한다")
    void createAssignsNextOrderIndex() {
        UUID roomId = UUID.randomUUID();
        Room room = createRoom(roomId);
        Schedule schedule = createSchedule(room, 100L);
        ScheduleItem savedItem = ScheduleItem.create(
                schedule,
                "place-1",
                LocalTime.of(9, 0),
                120,
                3
        );
        Instant createdAt = Instant.parse("2026-04-20T00:00:00Z");

        ReflectionTestUtils.setField(savedItem, "id", 200L);
        ReflectionTestUtils.setField(savedItem, "createdAt", createdAt);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.findByIdAndRoom_Id(100L, roomId)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findMaxOrderIndexBySchedule_Id(100L)).willReturn(Optional.of(2));
        given(scheduleItemRepository.saveAndFlush(any(ScheduleItem.class))).willReturn(savedItem);

        ScheduleItemResult result = scheduleItemService.create(
                roomId,
                100L,
                new ScheduleItemCreateCommand("place-1", LocalTime.of(9, 0), 120)
        );

        assertThat(result.itemId()).isEqualTo(200L);
        assertThat(result.scheduleId()).isEqualTo(100L);
        assertThat(result.orderIndex()).isEqualTo(3);
        assertThat(result.createdAt()).isEqualTo(createdAt);

        ArgumentCaptor<ScheduleItem> captor = ArgumentCaptor.forClass(ScheduleItem.class);
        verify(scheduleItemRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSchedule()).isSameAs(schedule);
        assertThat(captor.getValue().getOrderIndex()).isEqualTo(3);
    }

    @Test
    @DisplayName("일정 항목 삭제 시 남은 항목의 orderIndex를 0부터 연속되게 재정렬한다")
    void deleteReordersRemainingItemsContiguously() {
        UUID roomId = UUID.randomUUID();
        Room room = createRoom(roomId);
        Schedule schedule = createSchedule(room, 100L);
        ScheduleItem first = createScheduleItem(schedule, 10L, "place-1", 0);
        ScheduleItem second = createScheduleItem(schedule, 11L, "place-2", 1);
        ScheduleItem third = createScheduleItem(schedule, 12L, "place-3", 2);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.findByIdAndRoom_Id(100L, roomId)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findByIdAndSchedule_Id(11L, 100L)).willReturn(Optional.of(second));
        given(scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(100L))
                .willReturn(List.of(first, second, third));

        scheduleItemService.delete(roomId, 100L, 11L);

        verify(scheduleItemRepository).delete(second);
        assertThat(first.getOrderIndex()).isEqualTo(0);
        assertThat(third.getOrderIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("일정 항목 수정 시 시간 정보를 덮어쓴 결과를 반환한다")
    void updateOverwritesTimeInfo() {
        UUID roomId = UUID.randomUUID();
        Room room = createRoom(roomId);
        Schedule schedule = createSchedule(room, 100L);
        ScheduleItem item = ScheduleItem.create(
                schedule,
                "place-1",
                LocalTime.of(9, 0),
                120,
                0
        );
        Instant createdAt = Instant.parse("2026-04-20T00:00:00Z");

        ReflectionTestUtils.setField(item, "id", 10L);
        ReflectionTestUtils.setField(item, "createdAt", createdAt);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.findByIdAndRoom_Id(100L, roomId)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findByIdAndSchedule_Id(10L, 100L)).willReturn(Optional.of(item));
        given(scheduleItemRepository.saveAndFlush(item)).willReturn(item);

        ScheduleItemResult result = scheduleItemService.update(
                roomId,
                100L,
                10L,
                new ScheduleItemUpdateCommand(LocalTime.of(11, 30), 90)
        );

        assertThat(result.startTime()).isEqualTo(LocalTime.of(11, 30));
        assertThat(result.durationMinutes()).isEqualTo(90);
        assertThat(item.getStartTime()).isEqualTo(LocalTime.of(11, 30));
        assertThat(item.getDurationMinutes()).isEqualTo(90);
    }

    @Test
    @DisplayName("방이 없으면 일정 항목 조회 시 ROOM_NOT_FOUND 예외를 던진다")
    void getItemsThrowsWhenRoomMissing() {
        UUID roomId = UUID.randomUUID();

        given(roomRepository.findById(roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleItemService.getItems(roomId, 100L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("방의 일정이 아니면 일정 항목 수정 시 SCHEDULE_NOT_FOUND 예외를 던진다")
    void updateThrowsWhenScheduleMissingInRoom() {
        UUID roomId = UUID.randomUUID();
        Room room = createRoom(roomId);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.findByIdAndRoom_Id(100L, roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleItemService.update(
                roomId,
                100L,
                10L,
                new ScheduleItemUpdateCommand(LocalTime.of(11, 30), 90)
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    @DisplayName("일정 항목이 없으면 삭제 시 SCHEDULE_ITEM_NOT_FOUND 예외를 던진다")
    void deleteThrowsWhenItemMissing() {
        UUID roomId = UUID.randomUUID();
        Room room = createRoom(roomId);
        Schedule schedule = createSchedule(room, 100L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.findByIdAndRoom_Id(100L, roomId)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findByIdAndSchedule_Id(10L, 100L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleItemService.delete(roomId, 100L, 10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_ITEM_NOT_FOUND);
    }

    private Room createRoom(UUID roomId) {
        Room room = Room.create(
                "도쿄 여행",
                "도쿄",
                LocalDate.of(2026, 4, 20),
                LocalDate.of(2026, 4, 23),
                "INVITE",
                1L
        );
        ReflectionTestUtils.setField(room, "id", roomId);
        return room;
    }

    private Schedule createSchedule(Room room, Long scheduleId) {
        Schedule schedule = Schedule.create(room, 1, LocalDate.of(2026, 4, 20));
        ReflectionTestUtils.setField(schedule, "id", scheduleId);
        return schedule;
    }

    private ScheduleItem createScheduleItem(Schedule schedule, Long itemId, String googlePlaceId, int orderIndex) {
        ScheduleItem item = ScheduleItem.create(
                schedule,
                googlePlaceId,
                LocalTime.of(9, 0),
                60,
                orderIndex
        );
        ReflectionTestUtils.setField(item, "id", itemId);
        return item;
    }
}
