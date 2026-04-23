package com.howaboutus.backend.schedules.service;

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
import java.util.List;
import java.util.UUID;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleItemService {

    private final RoomRepository roomRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleItemRepository scheduleItemRepository;

    @Transactional
    public ScheduleItemResult create(UUID roomId, Long scheduleId, ScheduleItemCreateCommand command) {
        getRoom(roomId);
        Schedule schedule = getScheduleForWrite(roomId, scheduleId);
        int nextOrderIndex = scheduleItemRepository.findMaxOrderIndexBySchedule_Id(scheduleId)
                .map(maxOrderIndex -> maxOrderIndex + 1)
                .orElse(0);

        ScheduleItem scheduleItem = ScheduleItem.create(
                schedule,
                command.googlePlaceId(),
                command.startTime(),
                command.durationMinutes(),
                nextOrderIndex
        );
        return ScheduleItemResult.from(scheduleItemRepository.saveAndFlush(scheduleItem));
    }

    public List<ScheduleItemResult> getItems(UUID roomId, Long scheduleId) {
        getRoom(roomId);
        getSchedule(roomId, scheduleId);
        return scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(scheduleId)
                .stream()
                .map(ScheduleItemResult::from)
                .toList();
    }

    @Transactional
    public ScheduleItemResult update(UUID roomId, Long scheduleId, Long itemId, ScheduleItemUpdateCommand command) {
        getRoom(roomId);
        getScheduleForWrite(roomId, scheduleId);
        ScheduleItem scheduleItem = getScheduleItem(scheduleId, itemId);

        LocalTime startTime = command.startTimeProvided() ? command.startTime() : scheduleItem.getStartTime();
        Integer durationMinutes = command.durationMinutesProvided()
                ? command.durationMinutes()
                : scheduleItem.getDurationMinutes();
        scheduleItem.updateTimeInfo(startTime, durationMinutes);
        return ScheduleItemResult.from(scheduleItemRepository.saveAndFlush(scheduleItem));
    }

    @Transactional
    public void delete(UUID roomId, Long scheduleId, Long itemId) {
        getRoom(roomId);
        getScheduleForWrite(roomId, scheduleId);
        ScheduleItem scheduleItem = getScheduleItem(scheduleId, itemId);

        scheduleItemRepository.delete(scheduleItem);
        reorderRemainingItems(scheduleId, itemId);
    }

    @Transactional
    public void deleteAllByScheduleId(Long scheduleId) {
        scheduleItemRepository.deleteAllBySchedule_Id(scheduleId);
    }

    private void reorderRemainingItems(Long scheduleId, Long deletedItemId) {
        List<ScheduleItem> remainingItems = scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(scheduleId);
        int nextOrderIndex = 0;
        for (ScheduleItem remainingItem : remainingItems) {
            if (remainingItem.getId().equals(deletedItemId)) {
                continue;
            }
            remainingItem.changeOrderIndex(nextOrderIndex++);
        }
    }

    private Room getRoom(UUID roomId) {
        return roomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
    }

    private Schedule getSchedule(UUID roomId, Long scheduleId) {
        return scheduleRepository.findByIdAndRoom_Id(scheduleId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    private Schedule getScheduleForWrite(UUID roomId, Long scheduleId) {
        return scheduleRepository.findByIdAndRoom_IdWithOptimisticLock(scheduleId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    private ScheduleItem getScheduleItem(Long scheduleId, Long itemId) {
        return scheduleItemRepository.findByIdAndSchedule_Id(itemId, scheduleId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_ITEM_NOT_FOUND));
    }
}
