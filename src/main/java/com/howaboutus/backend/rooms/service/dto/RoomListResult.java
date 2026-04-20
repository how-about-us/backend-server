package com.howaboutus.backend.rooms.service.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RoomListResult(
        List<RoomSummary> rooms,
        Instant nextCursor,
        boolean hasNext
) {
    public record RoomSummary(
            UUID id,
            String title,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            long memberCount,
            String role,
            Instant joinedAt
    ) {
    }
}
