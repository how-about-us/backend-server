package com.howaboutus.backend.schedules.service.dto;

import java.time.LocalDate;

public record ScheduleCreateCommand(
        int dayNumber,
        LocalDate date
) {
}
