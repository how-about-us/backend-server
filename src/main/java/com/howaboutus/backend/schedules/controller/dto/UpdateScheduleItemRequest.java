package com.howaboutus.backend.schedules.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.howaboutus.backend.schedules.service.dto.ScheduleItemUpdateCommand;
import jakarta.validation.constraints.Positive;
import java.time.LocalTime;
import lombok.Getter;

@Getter
public class UpdateScheduleItemRequest {

    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @Positive(message = "durationMinutes는 1 이상이어야 합니다")
    private Integer durationMinutes;

    @JsonIgnore
    private boolean startTimeProvided;

    @JsonIgnore
    private boolean durationMinutesProvided;

    @JsonSetter("startTime")
    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
        this.startTimeProvided = true;
    }

    @JsonSetter("durationMinutes")
    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
        this.durationMinutesProvided = true;
    }

    public ScheduleItemUpdateCommand toCommand() {
        return new ScheduleItemUpdateCommand(
                startTime,
                durationMinutes,
                startTimeProvided,
                durationMinutesProvided
        );
    }
}
