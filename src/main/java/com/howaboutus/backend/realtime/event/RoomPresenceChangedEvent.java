package com.howaboutus.backend.realtime.event;

import com.howaboutus.backend.realtime.service.dto.RoomPresenceEventType;
import java.util.UUID;

public record RoomPresenceChangedEvent(
        UUID roomId,
        Long userId,
        RoomPresenceEventType type
) {
}
