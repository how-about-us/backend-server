package com.howaboutus.backend.schedules.controller.dto;

import com.howaboutus.backend.schedules.service.dto.ScheduleCreateCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateScheduleRequest(
        @NotNull(message = "dayNumber는 필수입니다")
        @Min(value = 1, message = "dayNumber는 1 이상이어야 합니다")
        Integer dayNumber,
        @NotNull(message = "date는 필수입니다")
        LocalDate date
) {

    public ScheduleCreateCommand toCommand() {
        return new ScheduleCreateCommand(dayNumber, date);
    }
}
