package com.howaboutus.backend.rooms.service.dto;

import java.time.LocalDate;

public record RoomCreateCommand(
        String title,
        String destination,
        LocalDate startDate,
        LocalDate endDate
) {
}
