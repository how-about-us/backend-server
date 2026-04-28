package com.howaboutus.backend.schedules.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.realtime.event.RoomScheduleChangedEvent;
import com.howaboutus.backend.realtime.service.dto.RoomScheduleEventType;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.rooms.service.RoomAuthorizationService;
import com.howaboutus.backend.schedules.entity.Schedule;
import com.howaboutus.backend.schedules.repository.ScheduleRepository;
import com.howaboutus.backend.schedules.service.dto.ScheduleCreateCommand;
import com.howaboutus.backend.schedules.service.dto.ScheduleResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleItemService scheduleItemService;

    @Mock
    private RoomAuthorizationService roomAuthorizationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(roomRepository, scheduleRepository, scheduleItemService,
                roomAuthorizationService, eventPublisher);
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
        Instant createdAt = Instant.parse("2026-04-17T00:00:00Z");
        ReflectionTestUtils.setField(schedule, "createdAt", createdAt);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.existsByRoom_IdAndDayNumber(roomId, 2)).willReturn(false);
        given(scheduleRepository.existsByRoom_IdAndDate(roomId, LocalDate.of(2026, 4, 21))).willReturn(false);
        given(scheduleRepository.saveAndFlush(any(Schedule.class))).willReturn(schedule);

        ScheduleResult result = scheduleService.create(roomId, command, 1L);

        assertThat(result.scheduleId()).isEqualTo(10L);
        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.dayNumber()).isEqualTo(2);
        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 4, 21));
        assertThat(result.createdAt()).isEqualTo(createdAt);
        ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
        var order = inOrder(roomAuthorizationService, scheduleRepository);
        order.verify(roomAuthorizationService).requireActiveMember(roomId, 1L);
        order.verify(scheduleRepository).existsByRoom_IdAndDayNumber(roomId, 2);
        order.verify(scheduleRepository).existsByRoom_IdAndDate(roomId, LocalDate.of(2026, 4, 21));
        order.verify(scheduleRepository).saveAndFlush(scheduleCaptor.capture());
        assertThat(scheduleCaptor.getValue().getRoom()).isSameAs(room);
        assertThat(scheduleCaptor.getValue().getDayNumber()).isEqualTo(2);
        assertThat(scheduleCaptor.getValue().getDate()).isEqualTo(LocalDate.of(2026, 4, 21));
        verify(eventPublisher).publishEvent(new RoomScheduleChangedEvent(
                roomId,
                1L,
                RoomScheduleEventType.SCHEDULE_CREATED,
                10L,
                null
        ));
    }

    @Test
    @DisplayName("방을 찾을 수 없으면 일정 생성 시 ROOM_NOT_FOUND 예외를 던진다")
    void createThrowsWhenRoomUnavailable() {
        UUID roomId = UUID.randomUUID();
        ScheduleCreateCommand command = new ScheduleCreateCommand(1, LocalDate.of(2026, 4, 20));

        given(roomRepository.findById(roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.create(roomId, command, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("방 멤버가 아니면 일정 생성 시 저장소 검증 전에 예외를 전파한다")
    void createPropagatesAuthorizationFailureBeforeScheduleRepositoryCalls() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 23), "INVITE", 1L);
        ScheduleCreateCommand command = new ScheduleCreateCommand(1, LocalDate.of(2026, 4, 20));
        CustomException authorizationFailure = new CustomException(ErrorCode.NOT_ROOM_MEMBER);

        ReflectionTestUtils.setField(room, "id", roomId);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        doThrow(authorizationFailure).when(roomAuthorizationService).requireActiveMember(roomId, 1L);

        assertThatThrownBy(() -> scheduleService.create(roomId, command, 1L))
                .isSameAs(authorizationFailure);

        verifyNoInteractions(scheduleRepository);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidScheduleCreateCommands")
    @DisplayName("일정 생성 요청이 여행 날짜 규칙을 어기면 SCHEDULE_DATE_MISMATCH 예외를 던진다")
    void createThrowsWhenScheduleDateInvalid(String ignoredScenario, ScheduleCreateCommand command) {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 23), "INVITE", 1L);

        ReflectionTestUtils.setField(room, "id", roomId);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> scheduleService.create(roomId, command, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_DATE_MISMATCH);
    }

    @Test
    @DisplayName("같은 방에 같은 일차가 있으면 SCHEDULE_ALREADY_EXISTS 예외를 던진다")
    void createThrowsWhenSameDayNumberExists() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 23), "INVITE", 1L);
        ScheduleCreateCommand command = new ScheduleCreateCommand(1, LocalDate.of(2026, 4, 20));

        ReflectionTestUtils.setField(room, "id", roomId);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.existsByRoom_IdAndDayNumber(roomId, 1)).willReturn(true);

        assertThatThrownBy(() -> scheduleService.create(roomId, command, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("같은 방에 같은 날짜가 있으면 SCHEDULE_ALREADY_EXISTS 예외를 던진다")
    void createThrowsWhenSameDateExists() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 23), "INVITE", 1L);
        ScheduleCreateCommand command = new ScheduleCreateCommand(1, LocalDate.of(2026, 4, 20));

        ReflectionTestUtils.setField(room, "id", roomId);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.existsByRoom_IdAndDayNumber(roomId, 1)).willReturn(false);
        given(scheduleRepository.existsByRoom_IdAndDate(roomId, LocalDate.of(2026, 4, 20))).willReturn(true);

        assertThatThrownBy(() -> scheduleService.create(roomId, command, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("저장 중 DB 중복이 발생하면 SCHEDULE_ALREADY_EXISTS 예외로 변환한다")
    void createTranslatesDatabaseDuplicateOnSave() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 23), "INVITE", 1L);
        ScheduleCreateCommand command = new ScheduleCreateCommand(1, LocalDate.of(2026, 4, 20));

        ReflectionTestUtils.setField(room, "id", roomId);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.existsByRoom_IdAndDayNumber(roomId, 1)).willReturn(false);
        given(scheduleRepository.existsByRoom_IdAndDate(roomId, LocalDate.of(2026, 4, 20))).willReturn(false);
        given(scheduleRepository.saveAndFlush(any(Schedule.class)))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> scheduleService.create(roomId, command, 1L))
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

        List<ScheduleResult> results = scheduleService.getSchedules(roomId, 1L);

        assertThat(results).containsExactly(ScheduleResult.from(first), ScheduleResult.from(second));
        assertThat(results.getFirst().dayNumber()).isEqualTo(1);
        var order = inOrder(roomAuthorizationService, scheduleRepository);
        order.verify(roomAuthorizationService).requireActiveMember(roomId, 1L);
        order.verify(scheduleRepository).findAllByRoom_IdOrderByDayNumberAsc(roomId);
    }

    @Test
    @DisplayName("방이 없으면 일정 목록 조회 시 ROOM_NOT_FOUND 예외를 던진다")
    void getSchedulesThrowsWhenRoomMissing() {
        UUID roomId = UUID.randomUUID();

        given(roomRepository.findById(roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.getSchedules(roomId, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("방 멤버가 아니면 일정 목록 조회 시 일정 저장소를 호출하지 않는다")
    void getSchedulesPropagatesAuthorizationFailureBeforeScheduleRepositoryCalls() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 23), "INVITE", 1L);
        CustomException authorizationFailure = new CustomException(ErrorCode.NOT_ROOM_MEMBER);

        ReflectionTestUtils.setField(room, "id", roomId);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        doThrow(authorizationFailure).when(roomAuthorizationService).requireActiveMember(roomId, 1L);

        assertThatThrownBy(() -> scheduleService.getSchedules(roomId, 1L))
                .isSameAs(authorizationFailure);

        verify(scheduleRepository, never()).findAllByRoom_IdOrderByDayNumberAsc(roomId);
    }

    @Test
    @DisplayName("방 밖의 일정은 삭제 시 SCHEDULE_NOT_FOUND 예외를 던진다")
    void deleteThrowsWhenScheduleOutsideRoom() {
        UUID roomId = UUID.randomUUID();
        given(roomRepository.findById(roomId))
                .willReturn(Optional.of(Room.create("도쿄 여행", "도쿄", LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 23), "INVITE", 1L)));
        given(scheduleRepository.findByIdAndRoom_Id(10L, roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.delete(roomId, 10L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    @DisplayName("방이 없으면 일정 삭제 시 ROOM_NOT_FOUND 예외를 던진다")
    void deleteThrowsWhenRoomMissing() {
        UUID roomId = UUID.randomUUID();

        given(roomRepository.findById(roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.delete(roomId, 10L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("방의 일정을 삭제하면 repository.delete를 호출한다")
    void deleteRemovesSchedule() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 23), "INVITE", 1L);
        Schedule schedule = Schedule.create(room, 1, LocalDate.of(2026, 4, 20));

        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(schedule, "id", 10L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.findByIdAndRoom_Id(10L, roomId)).willReturn(Optional.of(schedule));

        scheduleService.delete(roomId, 10L, 1L);

        var order = inOrder(roomAuthorizationService, scheduleRepository, scheduleItemService);
        order.verify(roomAuthorizationService).requireActiveMember(roomId, 1L);
        order.verify(scheduleRepository).findByIdAndRoom_Id(10L, roomId);
        order.verify(scheduleItemService).deleteAllByScheduleId(10L);
        order.verify(scheduleRepository).delete(schedule);
        verify(eventPublisher).publishEvent(new RoomScheduleChangedEvent(
                roomId,
                1L,
                RoomScheduleEventType.SCHEDULE_DELETED,
                10L,
                null
        ));
    }

    @Test
    @DisplayName("방 멤버가 아니면 일정 삭제 시 일정 저장소와 항목 서비스를 호출하지 않는다")
    void deletePropagatesAuthorizationFailureBeforeScheduleRepositoryCalls() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.create("도쿄 여행", "도쿄", LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 23), "INVITE", 1L);
        CustomException authorizationFailure = new CustomException(ErrorCode.NOT_ROOM_MEMBER);

        ReflectionTestUtils.setField(room, "id", roomId);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        doThrow(authorizationFailure).when(roomAuthorizationService).requireActiveMember(roomId, 1L);

        assertThatThrownBy(() -> scheduleService.delete(roomId, 10L, 1L))
                .isSameAs(authorizationFailure);

        verify(scheduleRepository, never()).findByIdAndRoom_Id(10L, roomId);
        verifyNoInteractions(scheduleItemService);
    }

    private static Stream<Arguments> invalidScheduleCreateCommands() {
        return Stream.of(
                Arguments.of("일차가 1보다 작으면 일정 생성 시 SCHEDULE_DATE_MISMATCH 예외를 던진다",
                        new ScheduleCreateCommand(0, LocalDate.of(2026, 4, 20))),
                Arguments.of("여행 기간 밖의 날짜로 일정 생성 시 SCHEDULE_DATE_MISMATCH 예외를 던진다",
                        new ScheduleCreateCommand(1, LocalDate.of(2026, 4, 24))),
                Arguments.of("일차와 날짜 조합이 맞지 않으면 일정 생성 시 SCHEDULE_DATE_MISMATCH 예외를 던진다",
                        new ScheduleCreateCommand(2, LocalDate.of(2026, 4, 22)))
        );
    }
}
