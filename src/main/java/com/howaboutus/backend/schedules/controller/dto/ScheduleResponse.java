package com.howaboutus.backend.schedules.controller.dto;

import com.howaboutus.backend.schedules.service.dto.ScheduleResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ScheduleResponse(
        Long scheduleId,
        UUID roomId,
        int dayNumber,
        LocalDate date,
        Instant createdAt
) {

    public static ScheduleResponse from(ScheduleResult result) {
        return new ScheduleResponse(
                result.scheduleId(),
                result.roomId(),
                result.dayNumber(),
                result.date(),
                result.createdAt()
        );
    }
}
