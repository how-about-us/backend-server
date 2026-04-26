package com.howaboutus.backend.schedules.service;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleItemService {

    private final RoomRepository roomRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final RoomAuthorizationService roomAuthorizationService;
    private final RouteService routeService;

    @Transactional
    public ScheduleItemResult create(UUID roomId, Long scheduleId, ScheduleItemCreateCommand command, Long userId) {
        getRoom(roomId);
        roomAuthorizationService.requireActiveMember(roomId, userId);
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

    public List<ScheduleItemResult> getItems(UUID roomId, Long scheduleId, Long userId) {
        getRoom(roomId);
        roomAuthorizationService.requireActiveMember(roomId, userId);
        getSchedule(roomId, scheduleId);
        return scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(scheduleId)
                .stream()
                .map(ScheduleItemResult::from)
                .toList();
    }

    @Transactional
    public ScheduleItemResult update(UUID roomId, Long scheduleId, Long itemId, ScheduleItemUpdateCommand command,
                                     Long userId) {
        getRoom(roomId);
        roomAuthorizationService.requireActiveMember(roomId, userId);
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
    public void delete(UUID roomId, Long scheduleId, Long itemId, Long userId) {
        getRoom(roomId);
        roomAuthorizationService.requireActiveMember(roomId, userId);
        getScheduleForWrite(roomId, scheduleId);
        ScheduleItem scheduleItem = getScheduleItem(scheduleId, itemId);

        scheduleItemRepository.delete(scheduleItem);
        reorderRemainingItems(scheduleId, itemId);
    }

    @Transactional
    public void deleteAllByScheduleId(Long scheduleId) {
        scheduleItemRepository.deleteAllBySchedule_Id(scheduleId);
    }

    @Transactional
    public List<ScheduleItemResult> reorder(UUID roomId, Long scheduleId, Long itemId, int newOrderIndex, Long userId) {
        getRoom(roomId);
        roomAuthorizationService.requireActiveMember(roomId, userId);
        getScheduleForWrite(roomId, scheduleId);

        List<ScheduleItem> items = scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(scheduleId);

        ScheduleItem movedItem = items.stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_ITEM_NOT_FOUND));

        if (newOrderIndex < 0 || newOrderIndex >= items.size()) {
            throw new CustomException(ErrorCode.INVALID_ORDER_INDEX);
        }

        items.remove(movedItem);
        items.add(newOrderIndex, movedItem);

        for (int i = 0; i < items.size(); i++) {
            items.get(i).changeOrderIndex(i);
        }

        return items.stream().map(ScheduleItemResult::from).toList();
    }

    @Transactional
    public ScheduleItemResult updateTravelMode(UUID roomId, Long scheduleId, Long itemId, String travelMode, Long userId) {
        getRoom(roomId);
        roomAuthorizationService.requireActiveMember(roomId, userId);
        getScheduleForWrite(roomId, scheduleId);
        ScheduleItem scheduleItem = getScheduleItem(scheduleId, itemId);
        scheduleItem.updateTravelMode(travelMode);
        return ScheduleItemResult.from(scheduleItemRepository.saveAndFlush(scheduleItem));
    }

    public Optional<RouteResult> getRouteForItem(UUID roomId, Long scheduleId, Long itemId, String travelModeOverride, Long userId) {
        getRoom(roomId);
        roomAuthorizationService.requireActiveMember(roomId, userId);
        getSchedule(roomId, scheduleId);

        List<ScheduleItem> items = scheduleItemRepository.findAllBySchedule_IdOrderByOrderIndexAsc(scheduleId);

        ScheduleItem current = null;
        ScheduleItem next = null;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId().equals(itemId)) {
                current = items.get(i);
                if (i + 1 < items.size()) {
                    next = items.get(i + 1);
                }
                break;
            }
        }

        if (current == null) {
            throw new CustomException(ErrorCode.SCHEDULE_ITEM_NOT_FOUND);
        }
        if (next == null) {
            return Optional.empty();
        }

        String mode;
        if (travelModeOverride != null) {
            mode = travelModeOverride;
        } else if (current.getTravelMode() != null) {
            mode = current.getTravelMode();
        } else {
            mode = "DRIVING";
        }
        return Optional.of(routeService.computeRoute(current.getGooglePlaceId(), next.getGooglePlaceId(), mode));
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
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
    }

    private Schedule getSchedule(UUID roomId, Long scheduleId) {
        return scheduleRepository.findByIdAndRoom_Id(scheduleId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    private Schedule getScheduleForWrite(UUID roomId, Long scheduleId) {
        Schedule schedule = scheduleRepository.findByIdAndRoom_Id(scheduleId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_NOT_FOUND));
        int updatedRows = scheduleRepository.incrementVersionIfCurrent(scheduleId, roomId, schedule.getVersion());
        if (updatedRows == 0) {
            throw new ObjectOptimisticLockingFailureException(Schedule.class, scheduleId);
        }
        return schedule;
    }

    private ScheduleItem getScheduleItem(Long scheduleId, Long itemId) {
        return scheduleItemRepository.findByIdAndSchedule_Id(itemId, scheduleId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCHEDULE_ITEM_NOT_FOUND));
    }
}
