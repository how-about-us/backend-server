package com.howaboutus.backend.schedules.service;

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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private final RoomRepository roomRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleItemService scheduleItemService;
    private final RoomAuthorizationService roomAuthorizationService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ScheduleResult create(UUID roomId, ScheduleCreateCommand command, Long userId) {
        Room room = getRoom(roomId);
        roomAuthorizationService.requireActiveMember(roomId, userId);
        validateScheduleDate(room, command.dayNumber(), command.date());
        if (scheduleRepository.existsByRoom_IdAndDayNumber(roomId, command.dayNumber())
                || scheduleRepository.existsByRoom_IdAndDate(roomId, command.date())) {
            throw new CustomException(ErrorCode.SCHEDULE_ALREADY_EXISTS);
        }

        Schedule schedule = Schedule.create(room, command.dayNumber(), command.date());
        try {
            ScheduleResult result = ScheduleResult.from(scheduleRepository.saveAndFlush(schedule));
            publishChanged(roomId, userId, RoomScheduleEventType.SCHEDULE_CREATED, result.scheduleId(), null);
            return result;
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.SCHEDULE_ALREADY_EXISTS, e);
        }
    }

    public List<ScheduleResult> getSchedules(UUID roomId, Long userId) {
        getRoom(roomId);
        roomAuthorizationService.requireActiveMember(roomId, userId);
        return scheduleRepository.findAllByRoom_IdOrderByDayNumberAsc(roomId)
                .stream()
                .map(ScheduleResult::from)
                .toList();
    }

    @Transactional
    public void delete(UUID roomId, Long scheduleId, Long userId) {
        getRoom(roomId);
        roomAuthorizationService.requireActiveMember(roomId, userId);
        Schedule schedule = scheduleRepository.findByIdAndRoom_Id(scheduleId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_NOT_FOUND));
        scheduleItemService.deleteAllByScheduleId(scheduleId);
        scheduleRepository.delete(schedule);
        publishChanged(roomId, userId, RoomScheduleEventType.SCHEDULE_DELETED, scheduleId, null);
    }

    private Room getRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
    }

    private void validateScheduleDate(Room room, int dayNumber, LocalDate date) {
        if (dayNumber < 1) {
            throw new CustomException(ErrorCode.SCHEDULE_DATE_MISMATCH);
        }
        if (room.getStartDate() == null || room.getEndDate() == null) {
            throw new CustomException(ErrorCode.SCHEDULE_DATE_MISMATCH);
        }
        if (date.isBefore(room.getStartDate()) || date.isAfter(room.getEndDate())) {
            throw new CustomException(ErrorCode.SCHEDULE_DATE_MISMATCH);
        }

        LocalDate expectedDate = room.getStartDate().plusDays(dayNumber - 1L);
        if (!expectedDate.equals(date)) {
            throw new CustomException(ErrorCode.SCHEDULE_DATE_MISMATCH);
        }
    }

    private void publishChanged(UUID roomId, Long actorUserId, RoomScheduleEventType type, Long scheduleId,
                                Long itemId) {
        eventPublisher.publishEvent(new RoomScheduleChangedEvent(roomId, actorUserId, type, scheduleId, itemId));
    }
}
