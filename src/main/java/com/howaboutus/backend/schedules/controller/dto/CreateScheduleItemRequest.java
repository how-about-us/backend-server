package com.howaboutus.backend.schedules.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.howaboutus.backend.schedules.service.dto.ScheduleItemCreateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.time.LocalTime;

public record CreateScheduleItemRequest(
        @NotBlank(message = "googlePlaceId는 공백일 수 없습니다")
        @Size(max = 300, message = "googlePlaceId는 300자 이하여야 합니다")
        String googlePlaceId,
        @JsonFormat(pattern = "HH:mm")
        @NotNull(message = "startTime는 필수입니다")
        LocalTime startTime,
        @NotNull(message = "durationMinutes는 필수입니다")
        @Positive(message = "durationMinutes는 1 이상이어야 합니다")
        Integer durationMinutes
) {
    public ScheduleItemCreateCommand toCommand() {
        return new ScheduleItemCreateCommand(googlePlaceId, startTime, durationMinutes);
    }
}
