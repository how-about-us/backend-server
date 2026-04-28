package com.howaboutus.backend.realtime.service.dto;

import java.util.UUID;

public record RoomSchedulePayload(
        UUID roomId,
        Long actorUserId,
        RoomScheduleEventType type,
        Long scheduleId,
        Long itemId
) {
}
