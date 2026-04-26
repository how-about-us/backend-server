package com.howaboutus.backend.schedules.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.howaboutus.backend.schedules.service.dto.ScheduleItemResult;
import java.time.Instant;
import java.time.LocalTime;

public record ScheduleItemResponse(
        Long itemId,
        Long scheduleId,
        String googlePlaceId,
        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,
        Integer durationMinutes,
        int orderIndex,
        Instant createdAt
) {
    public static ScheduleItemResponse from(ScheduleItemResult result) {
        return new ScheduleItemResponse(
                result.itemId(),
                result.scheduleId(),
                result.googlePlaceId(),
                result.startTime(),
                result.durationMinutes(),
                result.orderIndex(),
                result.createdAt()
        );
    }
}
