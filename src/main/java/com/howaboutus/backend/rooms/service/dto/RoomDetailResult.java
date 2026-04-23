package com.howaboutus.backend.rooms.service.dto;

import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.entity.RoomRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RoomDetailResult(
        UUID id,
        String title,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        String inviteCode,
        long memberCount,
        RoomRole role,
        Instant createdAt
) {
    public static RoomDetailResult of(Room room, RoomRole role, long memberCount) {
        return new RoomDetailResult(
                room.getId(), room.getTitle(), room.getDestination(),
                room.getStartDate(), room.getEndDate(), room.getInviteCode(),
                memberCount, role, room.getCreatedAt());
    }
}
