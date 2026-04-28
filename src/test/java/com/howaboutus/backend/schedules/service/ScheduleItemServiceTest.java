package com.howaboutus.backend.schedules.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.realtime.event.RoomScheduleChangedEvent;
import com.howaboutus.backend.realtime.service.dto.RoomScheduleEventType;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.rooms.service.RoomAuthorizationService;
import com.howaboutus.backend.schedules.entity.Schedule;
import com.howaboutus.backend.schedules.entity.ScheduleItem;
import com.howaboutus.backend.schedules.repository.ScheduleItemRepository;
import com.howaboutus.backend.schedules.repository.ScheduleRepository;
import com.howaboutus.backend.schedules.service.dto.RouteResult;
import com.howaboutus.backend.schedules.service.dto.ScheduleItemCreateCommand;
import com.howaboutus.backend.schedules.service.dto.ScheduleItemResult;
import com.howaboutus.backend.schedules.service.dto.ScheduleItemUpdateCommand;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ScheduleItemServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleItemRepository scheduleItemRepository;

    @Mock
    private RoomAuthorizationService roomAuthorizationService;

    @Mock
    private RouteService routeService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ScheduleItemService scheduleItemService;

    @BeforeEach
    void setUp() {
        scheduleItemService = new ScheduleItemService(roomRepository, scheduleRepository, scheduleItemRepository,
                roomAuthorizationService, routeService, eventPublisher);
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
        givenScheduleForWrite(roomId, schedule);
        given(scheduleItemRepository.findMaxOrderIndexBySchedule_Id(100L)).willReturn(Optional.of(2));
        given(scheduleItemRepository.saveAndFlush(any(ScheduleItem.class))).willReturn(savedItem);

        ScheduleItemResult result = scheduleItemService.create(
                roomId,
                100L,
                new ScheduleItemCreateCommand("place-1", LocalTime.of(9, 0), 120),
                1L
        );

        assertThat(result.itemId()).isEqualTo(200L);
        assertThat(result.scheduleId()).isEqualTo(100L);
        assertThat(result.orderIndex()).isEqualTo(3);
        assertThat(result.createdAt()).isEqualTo(createdAt);

        ArgumentCaptor<ScheduleItem> captor = ArgumentCaptor.forClass(ScheduleItem.class);
        verify(scheduleItemRepository).saveAndFlush(captor.capture());
        verify(scheduleRepository).findByIdAndRoom_Id(100L, roomId);
        verify(scheduleRepository).incrementVersionIfCurrent(100L, roomId, 0L);
        assertThat(captor.getValue().getSchedule()).isSameAs(schedule);
        assertThat(captor.getValue().getOrderIndex()).isEqualTo(3);
        verify(eventPublisher).publishEvent(new RoomScheduleChangedEvent(
                roomId,
                1L,
                RoomScheduleEventType.SCHEDULE_ITEM_CREATED,
                100L,
                200L
        ));
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
        givenScheduleForWrite(roomId, schedule);
        given(scheduleItemRepository.findByIdAndSchedule_Id(11L, 100L)).willReturn(Optional.of(second));
        given(scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(100L))
                .willReturn(List.of(first, second, third));

        scheduleItemService.delete(roomId, 100L, 11L, 1L);

        verify(scheduleRepository).findByIdAndRoom_Id(100L, roomId);
        verify(scheduleRepository).incrementVersionIfCurrent(100L, roomId, 0L);
        verify(scheduleItemRepository).delete(second);
        assertThat(first.getOrderIndex()).isZero();
        assertThat(third.getOrderIndex()).isEqualTo(1);
        verify(eventPublisher).publishEvent(new RoomScheduleChangedEvent(
                roomId,
                1L,
                RoomScheduleEventType.SCHEDULE_ITEM_DELETED,
                100L,
                11L
        ));
    }

    @Test
    @DisplayName("일정 항목 수정 시 전달하지 않은 필드는 기존 값을 유지한다")
    void updatePreservesExistingFieldsWhenNotProvided() {
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
        givenScheduleForWrite(roomId, schedule);
        given(scheduleItemRepository.findByIdAndSchedule_Id(10L, 100L)).willReturn(Optional.of(item));
        given(scheduleItemRepository.saveAndFlush(item)).willReturn(item);

        ScheduleItemResult result = scheduleItemService.update(
                roomId,
                100L,
                10L,
                new ScheduleItemUpdateCommand(null, 90, false, true),
                1L
        );

        assertThat(result.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(result.durationMinutes()).isEqualTo(90);
        verify(scheduleRepository).findByIdAndRoom_Id(100L, roomId);
        verify(scheduleRepository).incrementVersionIfCurrent(100L, roomId, 0L);
        assertThat(item.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(item.getDurationMinutes()).isEqualTo(90);
        verify(eventPublisher).publishEvent(new RoomScheduleChangedEvent(
                roomId,
                1L,
                RoomScheduleEventType.SCHEDULE_ITEM_UPDATED,
                100L,
                10L
        ));
    }

    @Test
    @DisplayName("일정 항목 수정 시 null로 명시한 필드는 제거한다")
    void updateClearsFieldWhenExplicitNullProvided() {
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

        ReflectionTestUtils.setField(item, "id", 10L);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        givenScheduleForWrite(roomId, schedule);
        given(scheduleItemRepository.findByIdAndSchedule_Id(10L, 100L)).willReturn(Optional.of(item));
        given(scheduleItemRepository.saveAndFlush(item)).willReturn(item);

        ScheduleItemResult result = scheduleItemService.update(
                roomId,
                100L,
                10L,
                new ScheduleItemUpdateCommand(null, 120, true, false),
                1L
        );

        assertThat(result.startTime()).isNull();
        assertThat(result.durationMinutes()).isEqualTo(120);
        assertThat(item.getStartTime()).isNull();
        assertThat(item.getDurationMinutes()).isEqualTo(120);
    }

    @Test
    @DisplayName("일정 버전 선점에 실패하면 항목 생성 전 낙관적 락 예외를 던진다")
    void createThrowsOptimisticLockingFailureWhenScheduleVersionAlreadyChanged() {
        UUID roomId = UUID.randomUUID();
        Room room = createRoom(roomId);
        Schedule schedule = createSchedule(room, 100L);
        ScheduleItemCreateCommand command = new ScheduleItemCreateCommand("place-1", LocalTime.of(9, 0), 120);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.findByIdAndRoom_Id(100L, roomId)).willReturn(Optional.of(schedule));
        given(scheduleRepository.incrementVersionIfCurrent(100L, roomId, 0L)).willReturn(0);

        assertThatThrownBy(() -> scheduleItemService.create(roomId, 100L, command, 1L))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(scheduleItemRepository, never()).saveAndFlush(any(ScheduleItem.class));
    }

    @Test
    @DisplayName("일정 항목 목록 조회 시 orderIndex 오름차순 결과를 ScheduleItemResult로 반환한다")
    void getItemsReturnsOrderedMappedResults() {
        UUID roomId = UUID.randomUUID();
        Room room = createRoom(roomId);
        Schedule schedule = createSchedule(room, 100L);
        ScheduleItem first = createScheduleItem(schedule, 10L, "place-1", 0);
        ScheduleItem second = createScheduleItem(schedule, 11L, "place-2", 1);
        Instant firstCreatedAt = Instant.parse("2026-04-20T00:00:00Z");
        Instant secondCreatedAt = Instant.parse("2026-04-20T01:00:00Z");

        ReflectionTestUtils.setField(first, "createdAt", firstCreatedAt);
        ReflectionTestUtils.setField(second, "createdAt", secondCreatedAt);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.findByIdAndRoom_Id(100L, roomId)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(100L))
                .willReturn(List.of(first, second));

        List<ScheduleItemResult> results = scheduleItemService.getItems(roomId, 100L, 1L);

        verify(scheduleRepository).findByIdAndRoom_Id(100L, roomId);
        assertThat(results).containsExactly(
                ScheduleItemResult.from(first),
                ScheduleItemResult.from(second)
        );
        assertThat(results.getFirst().orderIndex()).isZero();
        assertThat(results.get(0).googlePlaceId()).isEqualTo("place-1");
        assertThat(results.get(1).orderIndex()).isEqualTo(1);
        assertThat(results.get(1).googlePlaceId()).isEqualTo("place-2");
    }

    @Test
    @DisplayName("방이 없으면 일정 항목 조회 시 ROOM_NOT_FOUND 예외를 던진다")
    void getItemsThrowsWhenRoomMissing() {
        UUID roomId = UUID.randomUUID();

        given(roomRepository.findById(roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleItemService.getItems(roomId, 100L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("방의 일정이 아니면 일정 항목 수정 시 SCHEDULE_NOT_FOUND 예외를 던진다")
    void updateThrowsWhenScheduleMissingInRoom() {
        UUID roomId = UUID.randomUUID();
        Room room = createRoom(roomId);
        ScheduleItemUpdateCommand command = new ScheduleItemUpdateCommand(LocalTime.of(11, 30), 90, true, true);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.findByIdAndRoom_Id(100L, roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleItemService.update(roomId, 100L, 10L, command, 1L))
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
        givenScheduleForWrite(roomId, schedule);
        given(scheduleItemRepository.findByIdAndSchedule_Id(10L, 100L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleItemService.delete(roomId, 100L, 10L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("순서 변경 시 항목 목록을 새 순서로 반환한다")
    void reorderReturnsItemsInNewOrder() {
        UUID roomId = UUID.randomUUID();
        Room room = createRoom(roomId);
        Schedule schedule = createSchedule(room, 100L);
        ScheduleItem first = createScheduleItem(schedule, 10L, "place-1", 0);
        ScheduleItem second = createScheduleItem(schedule, 11L, "place-2", 1);
        ScheduleItem third = createScheduleItem(schedule, 12L, "place-3", 2);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        givenScheduleForWrite(roomId, schedule);
        given(scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(100L))
                .willReturn(new ArrayList<>(List.of(first, second, third)));

        List<ScheduleItemResult> results = scheduleItemService.reorder(roomId, 100L, 10L, 2, 1L);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).googlePlaceId()).isEqualTo("place-2");
        assertThat(results.get(1).googlePlaceId()).isEqualTo("place-3");
        assertThat(results.get(2).googlePlaceId()).isEqualTo("place-1");
        assertThat(results.get(2).orderIndex()).isEqualTo(2);
        verify(eventPublisher).publishEvent(new RoomScheduleChangedEvent(
                roomId,
                1L,
                RoomScheduleEventType.SCHEDULE_ITEMS_REORDERED,
                100L,
                10L
        ));
    }

    @Test
    @DisplayName("범위를 벗어난 순서 인덱스로 변경 시 INVALID_ORDER_INDEX 예외를 던진다")
    void reorderThrowsWhenIndexOutOfRange() {
        UUID roomId = UUID.randomUUID();
        Room room = createRoom(roomId);
        Schedule schedule = createSchedule(room, 100L);
        ScheduleItem first = createScheduleItem(schedule, 10L, "place-1", 0);
        ScheduleItem second = createScheduleItem(schedule, 11L, "place-2", 1);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        givenScheduleForWrite(roomId, schedule);
        given(scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(100L))
                .willReturn(new ArrayList<>(List.of(first, second)));

        assertThatThrownBy(() -> scheduleItemService.reorder(roomId, 100L, 10L, 5, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_ORDER_INDEX);
    }

    @Test
    @DisplayName("이동 수단 변경 시 변경된 항목을 반환한다")
    void updateTravelModeReturnsSavedItem() {
        UUID roomId = UUID.randomUUID();
        Room room = createRoom(roomId);
        Schedule schedule = createSchedule(room, 100L);
        ScheduleItem item = createScheduleItem(schedule, 10L, "place-1", 0);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        givenScheduleForWrite(roomId, schedule);
        given(scheduleItemRepository.findByIdAndSchedule_Id(10L, 100L)).willReturn(Optional.of(item));
        given(scheduleItemRepository.saveAndFlush(item)).willReturn(item);

        ScheduleItemResult result = scheduleItemService.updateTravelMode(roomId, 100L, 10L, "WALKING", 1L);

        assertThat(result.travelMode()).isEqualTo("WALKING");
        assertThat(item.getTravelMode()).isEqualTo("WALKING");
        verify(eventPublisher).publishEvent(new RoomScheduleChangedEvent(
                roomId,
                1L,
                RoomScheduleEventType.SCHEDULE_ITEM_TRAVEL_MODE_UPDATED,
                100L,
                10L
        ));
    }

    @Test
    @DisplayName("이동 수단 변경 시 공백과 대소문자를 정규화한다")
    void updateTravelModeNormalizesValue() {
        UUID roomId = UUID.randomUUID();
        Room room = createRoom(roomId);
        Schedule schedule = createSchedule(room, 100L);
        ScheduleItem item = createScheduleItem(schedule, 10L, "place-1", 0);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        givenScheduleForWrite(roomId, schedule);
        given(scheduleItemRepository.findByIdAndSchedule_Id(10L, 100L)).willReturn(Optional.of(item));
        given(scheduleItemRepository.saveAndFlush(item)).willReturn(item);

        ScheduleItemResult result = scheduleItemService.updateTravelMode(roomId, 100L, 10L, " walking ", 1L);

        assertThat(result.travelMode()).isEqualTo("WALKING");
        assertThat(item.getTravelMode()).isEqualTo("WALKING");
    }

    @Test
    @DisplayName("마지막 항목이 아니면 이동 정보를 반환한다")
    void getRouteForItemReturnsRouteWhenNotLast() {
        UUID roomId = UUID.randomUUID();
        Room room = createRoom(roomId);
        Schedule schedule = createSchedule(room, 100L);
        ScheduleItem first = createScheduleItem(schedule, 10L, "place-1", 0);
        ScheduleItem second = createScheduleItem(schedule, 11L, "place-2", 1);
        RouteResult expected = new RouteResult(500, 300, "DRIVING");

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.findByIdAndRoom_Id(100L, roomId)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(100L))
                .willReturn(List.of(first, second));
        given(routeService.computeRoute("place-1", "place-2", "DRIVING")).willReturn(expected);

        Optional<RouteResult> result = scheduleItemService.getRouteForItem(roomId, 100L, 10L, null, 1L);

        assertThat(result).contains(expected);
    }

    @Test
    @DisplayName("저장된 이동 수단이 있으면 이동 정보 조회 시 해당 값을 사용한다")
    void getRouteForItemUsesStoredTravelMode() {
        UUID roomId = UUID.randomUUID();
        Room room = createRoom(roomId);
        Schedule schedule = createSchedule(room, 100L);
        ScheduleItem first = createScheduleItem(schedule, 10L, "place-1", 0);
        ScheduleItem second = createScheduleItem(schedule, 11L, "place-2", 1);
        first.updateTravelMode("TRANSIT");
        RouteResult expected = new RouteResult(500, 300, "TRANSIT");

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.findByIdAndRoom_Id(100L, roomId)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(100L))
                .willReturn(List.of(first, second));
        given(routeService.computeRoute("place-1", "place-2", "TRANSIT")).willReturn(expected);

        Optional<RouteResult> result = scheduleItemService.getRouteForItem(roomId, 100L, 10L, null, 1L);

        assertThat(result).contains(expected);
    }

    @Test
    @DisplayName("이동 수단 override가 있으면 저장된 이동 수단보다 우선하고 정규화한다")
    void getRouteForItemUsesNormalizedOverrideBeforeStoredTravelMode() {
        UUID roomId = UUID.randomUUID();
        Room room = createRoom(roomId);
        Schedule schedule = createSchedule(room, 100L);
        ScheduleItem first = createScheduleItem(schedule, 10L, "place-1", 0);
        ScheduleItem second = createScheduleItem(schedule, 11L, "place-2", 1);
        first.updateTravelMode("TRANSIT");
        RouteResult expected = new RouteResult(700, 420, "WALKING");

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.findByIdAndRoom_Id(100L, roomId)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(100L))
                .willReturn(List.of(first, second));
        given(routeService.computeRoute("place-1", "place-2", "WALKING")).willReturn(expected);

        Optional<RouteResult> result = scheduleItemService.getRouteForItem(roomId, 100L, 10L, " walking ", 1L);

        assertThat(result).contains(expected);
    }

    @Test
    @DisplayName("마지막 항목이면 이동 정보를 반환하지 않는다")
    void getRouteForItemReturnsEmptyWhenLast() {
        UUID roomId = UUID.randomUUID();
        Room room = createRoom(roomId);
        Schedule schedule = createSchedule(room, 100L);
        ScheduleItem only = createScheduleItem(schedule, 10L, "place-1", 0);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(scheduleRepository.findByIdAndRoom_Id(100L, roomId)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(100L))
                .willReturn(List.of(only));

        Optional<RouteResult> result = scheduleItemService.getRouteForItem(roomId, 100L, 10L, null, 1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("방이 없으면 일정 항목 생성 시 ROOM_NOT_FOUND 예외를 던진다")
    void createThrowsWhenRoomMissing() {
        UUID roomId = UUID.randomUUID();
        ScheduleItemCreateCommand command = new ScheduleItemCreateCommand("place-1", LocalTime.of(9, 0), 120);

        given(roomRepository.findById(roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleItemService.create(roomId, 100L, command, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
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
        ReflectionTestUtils.setField(schedule, "version", 0L);
        return schedule;
    }

    private void givenScheduleForWrite(UUID roomId, Schedule schedule) {
        given(scheduleRepository.findByIdAndRoom_Id(schedule.getId(), roomId))
                .willReturn(Optional.of(schedule));
        given(scheduleRepository.incrementVersionIfCurrent(schedule.getId(), roomId, schedule.getVersion()))
                .willReturn(1);
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
