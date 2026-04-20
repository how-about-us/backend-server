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
import com.howaboutus.backend.schedules.repository.ScheduleRepository;
import com.howaboutus.backend.schedules.service.dto.ScheduleCreateCommand;
import com.howaboutus.backend.schedules.service.dto.ScheduleResult;
import java.time.LocalDate;
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
class ScheduleServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(roomRepository, scheduleRepository);
    }

    @Test
    @DisplayName("일정 생성 성공 시 저장된 값을 반환한다")
    void createReturnsSavedSchedule() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 23), "INVITE", 1L);
        Schedule schedule = Schedule.create(room, 2, LocalDate.of(2026, 4, 21));
        ScheduleCreateCommand command = new ScheduleCreateCommand(2, LocalDate.of(2026, 4, 21));

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(schedule, "id", 10L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.existsByRoom_IdAndDayNumber(roomId, 2)).willReturn(false);
        given(scheduleRepository.existsByRoom_IdAndDate(roomId, LocalDate.of(2026, 4, 21))).willReturn(false);
        given(scheduleRepository.saveAndFlush(any(Schedule.class))).willReturn(schedule);

        ScheduleResult result = scheduleService.create(roomId, command);

        assertThat(result).isEqualTo(ScheduleResult.from(schedule));

        ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository).saveAndFlush(scheduleCaptor.capture());
        assertThat(scheduleCaptor.getValue().getDayNumber()).isEqualTo(2);
        assertThat(scheduleCaptor.getValue().getDate()).isEqualTo(LocalDate.of(2026, 4, 21));
    }

    @Test
    @DisplayName("방이 없으면 일정 생성 시 ROOM_NOT_FOUND 예외를 던진다")
    void createThrowsWhenRoomMissing() {
        UUID roomId = UUID.randomUUID();

        given(roomRepository.findById(roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.create(roomId, new ScheduleCreateCommand(1, LocalDate.of(2026, 4, 20))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("일차가 1보다 작으면 일정 생성 시 SCHEDULE_DATE_MISMATCH 예외를 던진다")
    void createThrowsWhenDayNumberInvalid() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 23), "INVITE", 1L);

        ReflectionTestUtils.setField(room, "id", roomId);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> scheduleService.create(roomId, new ScheduleCreateCommand(0, LocalDate.of(2026, 4, 20))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_DATE_MISMATCH);
    }

    @Test
    @DisplayName("여행 기간 밖의 날짜로 일정 생성 시 SCHEDULE_DATE_MISMATCH 예외를 던진다")
    void createThrowsWhenDateOutsideTravelPeriod() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 23), "INVITE", 1L);

        ReflectionTestUtils.setField(room, "id", roomId);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> scheduleService.create(roomId, new ScheduleCreateCommand(1, LocalDate.of(2026, 4, 24))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_DATE_MISMATCH);
    }

    @Test
    @DisplayName("일차와 날짜 조합이 맞지 않으면 일정 생성 시 SCHEDULE_DATE_MISMATCH 예외를 던진다")
    void createThrowsWhenDateAndDayNumberMismatch() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 23), "INVITE", 1L);

        ReflectionTestUtils.setField(room, "id", roomId);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> scheduleService.create(roomId, new ScheduleCreateCommand(2, LocalDate.of(2026, 4, 22))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_DATE_MISMATCH);
    }

    @Test
    @DisplayName("같은 방에 같은 일차가 있으면 SCHEDULE_ALREADY_EXISTS 예외를 던진다")
    void createThrowsWhenSameDayNumberExists() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 23), "INVITE", 1L);

        ReflectionTestUtils.setField(room, "id", roomId);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.existsByRoom_IdAndDayNumber(roomId, 1)).willReturn(true);

        assertThatThrownBy(() -> scheduleService.create(roomId, new ScheduleCreateCommand(1, LocalDate.of(2026, 4, 20))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("같은 방에 같은 날짜가 있으면 SCHEDULE_ALREADY_EXISTS 예외를 던진다")
    void createThrowsWhenSameDateExists() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 23), "INVITE", 1L);

        ReflectionTestUtils.setField(room, "id", roomId);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.existsByRoom_IdAndDayNumber(roomId, 1)).willReturn(false);
        given(scheduleRepository.existsByRoom_IdAndDate(roomId, LocalDate.of(2026, 4, 20))).willReturn(true);

        assertThatThrownBy(() -> scheduleService.create(roomId, new ScheduleCreateCommand(1, LocalDate.of(2026, 4, 20))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("방이 있으면 일정 목록 조회 시 dayNumber 오름차순 결과를 반환한다")
    void getSchedulesReturnsOrderedResults() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 23), "INVITE", 1L);
        Schedule first = Schedule.create(room, 1, LocalDate.of(2026, 4, 20));
        Schedule second = Schedule.create(room, 2, LocalDate.of(2026, 4, 21));

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(first, "id", 10L);
        ReflectionTestUtils.setField(second, "id", 11L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.findAllByRoom_IdOrderByDayNumberAsc(roomId)).willReturn(List.of(first, second));

        List<ScheduleResult> results = scheduleService.getSchedules(roomId);

        assertThat(results).containsExactly(ScheduleResult.from(first), ScheduleResult.from(second));
        assertThat(results.getFirst().dayNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("방이 없으면 일정 목록 조회 시 ROOM_NOT_FOUND 예외를 던진다")
    void getSchedulesThrowsWhenRoomMissing() {
        UUID roomId = UUID.randomUUID();

        given(roomRepository.findById(roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.getSchedules(roomId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("방 밖의 일정은 삭제 시 SCHEDULE_NOT_FOUND 예외를 던진다")
    void deleteThrowsWhenScheduleOutsideRoom() {
        UUID roomId = UUID.randomUUID();
        given(scheduleRepository.findByIdAndRoom_Id(10L, roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.delete(roomId, 10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    @DisplayName("방의 일정을 삭제하면 repository.delete를 호출한다")
    void deleteRemovesSchedule() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 23), "INVITE", 1L);
        Schedule schedule = Schedule.create(room, 1, LocalDate.of(2026, 4, 20));

        ReflectionTestUtils.setField(schedule, "id", 10L);

        given(scheduleRepository.findByIdAndRoom_Id(10L, roomId)).willReturn(Optional.of(schedule));

        scheduleService.delete(roomId, 10L);

        verify(scheduleRepository).delete(schedule);
    }
}
