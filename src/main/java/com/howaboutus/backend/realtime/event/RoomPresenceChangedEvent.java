package com.howaboutus.backend.realtime.event;

import com.howaboutus.backend.realtime.service.dto.RoomPresenceEventType;
import java.util.UUID;

public record RoomPresenceChangedEvent(
        UUID roomId,
        Long userId,
        RoomPresenceEventType type,
        String nickname,
        String profileImageUrl
) {
    public RoomPresenceChangedEvent(UUID roomId, Long userId, RoomPresenceEventType type) {
        this(roomId, userId, type, null, null);
    }
}
