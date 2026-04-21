package com.howaboutus.backend.rooms.service.dto;

import java.time.LocalDate;

public record RoomUpdateCommand(
        String title,
        String destination,
        LocalDate startDate,
        LocalDate endDate
) {
}
