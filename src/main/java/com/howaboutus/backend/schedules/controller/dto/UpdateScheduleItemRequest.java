package com.howaboutus.backend.schedules.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.howaboutus.backend.schedules.service.dto.ScheduleItemUpdateCommand;
import jakarta.validation.constraints.Positive;
import java.time.LocalTime;

public record UpdateScheduleItemRequest(
        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,
        @Positive(message = "durationMinutes는 1 이상이어야 합니다")
        Integer durationMinutes
) {
    public ScheduleItemUpdateCommand toCommand() {
        return new ScheduleItemUpdateCommand(startTime, durationMinutes);
    }
}
