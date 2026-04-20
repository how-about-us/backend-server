package com.howaboutus.backend.rooms.controller.dto;

import com.howaboutus.backend.rooms.service.dto.RoomUpdateCommand;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateRoomRequest(
        @Size(max = 100, message = "방 제목은 100자 이하여야 합니다")
        String title,

        @Size(max = 200, message = "여행지는 200자 이하여야 합니다")
        String destination,

        LocalDate startDate,
        LocalDate endDate
) {
    public RoomUpdateCommand toCommand() {
        return new RoomUpdateCommand(title, destination, startDate, endDate);
    }
}
