package com.howaboutus.backend.schedules.service.dto;

import java.time.LocalTime;

public record ScheduleItemUpdateCommand(
        LocalTime startTime,
        Integer durationMinutes,
        boolean startTimeProvided,
        boolean durationMinutesProvided
) {
}
