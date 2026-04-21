package com.howaboutus.backend.rooms.controller.dto;

import com.howaboutus.backend.rooms.service.dto.RoomCreateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateRoomRequest(
        @NotBlank(message = "방 제목은 필수입니다")
        @Size(max = 100, message = "방 제목은 100자 이하여야 합니다")
        String title,

        @Size(max = 200, message = "여행지는 200자 이하여야 합니다")
        String destination,

        LocalDate startDate,
        LocalDate endDate
) {
    public RoomCreateCommand toCommand() {
        return new RoomCreateCommand(title, destination, startDate, endDate);
    }
}
