package com.howaboutus.backend.rooms.controller.dto;

import com.howaboutus.backend.rooms.service.dto.RoomListResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RoomListResponse(
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
            String role,
            Instant joinedAt
    ) {
    }

    public static RoomListResponse from(RoomListResult result) {
        List<RoomSummary> rooms = result.rooms().stream()
                .map(r -> new RoomSummary(
                        r.id(), r.title(), r.destination(),
                        r.startDate(), r.endDate(),
                        r.role(), r.joinedAt()))
                .toList();
        return new RoomListResponse(rooms, result.nextCursor(), result.hasNext());
    }
}
