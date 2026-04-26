package com.howaboutus.backend.schedules.service.dto;

import com.howaboutus.backend.schedules.entity.ScheduleItem;
import java.time.Instant;
import java.time.LocalTime;

public record ScheduleItemResult(
        long itemId,
        long scheduleId,
        String googlePlaceId,
        LocalTime startTime,
        Integer durationMinutes,
        int orderIndex,
        Instant createdAt
) {

    public static ScheduleItemResult from(ScheduleItem scheduleItem) {
        return new ScheduleItemResult(
                scheduleItem.getId(),
                scheduleItem.getSchedule().getId(),
                scheduleItem.getGooglePlaceId(),
                scheduleItem.getStartTime(),
                scheduleItem.getDurationMinutes(),
                scheduleItem.getOrderIndex(),
                scheduleItem.getCreatedAt()
        );
    }
}
