package com.howaboutus.backend.schedules.service.dto;

import java.time.LocalTime;

public record ScheduleItemCreateCommand(
        String googlePlaceId,
        LocalTime startTime,
        Integer durationMinutes
) {
}
