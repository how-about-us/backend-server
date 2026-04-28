package com.howaboutus.backend.realtime.service.dto;

import java.util.UUID;

public record RoomPresencePayload(
        UUID roomId,
        Long userId,
        RoomPresenceEventType type
) {
}
