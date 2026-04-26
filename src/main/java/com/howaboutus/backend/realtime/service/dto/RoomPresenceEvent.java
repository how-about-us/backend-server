package com.howaboutus.backend.realtime.service.dto;

import java.util.UUID;

public record RoomPresenceEvent(
        UUID roomId,
        Long userId,
        RoomPresenceEventType type
) {
}
