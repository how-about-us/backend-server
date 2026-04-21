package com.howaboutus.backend.rooms.controller.dto;

import com.howaboutus.backend.rooms.service.dto.RoomDetailResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RoomDetailResponse(
        UUID id,
        String title,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        String inviteCode,
        long memberCount,
        String role,
        Instant createdAt
) {
    public static RoomDetailResponse from(RoomDetailResult result) {
        return new RoomDetailResponse(
                result.id(), result.title(), result.destination(),
                result.startDate(), result.endDate(), result.inviteCode(),
                result.memberCount(), result.role().name(), result.createdAt());
    }
}
