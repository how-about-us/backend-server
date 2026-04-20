package com.howaboutus.backend.schedules.service.dto;

import com.howaboutus.backend.schedules.entity.Schedule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ScheduleResult(
        long scheduleId,
        UUID roomId,
        int dayNumber,
        LocalDate date,
        Instant createdAt
) {

    public static ScheduleResult from(Schedule schedule) {
        return new ScheduleResult(
                schedule.getId(),
                schedule.getRoom().getId(),
                schedule.getDayNumber(),
                schedule.getDate(),
                schedule.getCreatedAt()
        );
    }
}
