package com.howaboutus.backend.schedules.service;

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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private final RoomRepository roomRepository;
    private final ScheduleRepository scheduleRepository;

    @Transactional
    public ScheduleResult create(UUID roomId, ScheduleCreateCommand command) {
        Room room = getRoom(roomId);
        validateScheduleDate(room, command.dayNumber(), command.date());
        if (scheduleRepository.existsByRoom_IdAndDayNumber(roomId, command.dayNumber())
                || scheduleRepository.existsByRoom_IdAndDate(roomId, command.date())) {
            throw new CustomException(ErrorCode.SCHEDULE_ALREADY_EXISTS);
        }

        Schedule schedule = Schedule.create(room, command.dayNumber(), command.date());
        try {
            return ScheduleResult.from(scheduleRepository.saveAndFlush(schedule));
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.SCHEDULE_ALREADY_EXISTS, e);
        }
    }

    public List<ScheduleResult> getSchedules(UUID roomId) {
        getRoom(roomId);
        return scheduleRepository.findAllByRoom_IdOrderByDayNumberAsc(roomId)
                .stream()
                .map(ScheduleResult::from)
                .toList();
    }

    @Transactional
    public void delete(UUID roomId, Long scheduleId) {
        getRoom(roomId);
        Schedule schedule = scheduleRepository.findByIdAndRoom_Id(scheduleId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_NOT_FOUND));
        scheduleRepository.delete(schedule);
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
}
