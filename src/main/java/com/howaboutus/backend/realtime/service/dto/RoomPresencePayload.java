package com.howaboutus.backend.realtime.service.dto;

import java.util.UUID;

public record RoomPresencePayload(
        UUID roomId,
        Long userId,
        RoomPresenceEventType type,
        String nickname,
        String profileImageUrl
) {
    public RoomPresencePayload(UUID roomId, Long userId, RoomPresenceEventType type) {
        this(roomId, userId, type, null, null);
    }
}
