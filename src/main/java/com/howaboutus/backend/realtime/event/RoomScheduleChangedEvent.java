package com.howaboutus.backend.realtime.event;

import com.howaboutus.backend.realtime.service.dto.RoomScheduleEventType;
import java.util.UUID;

public record RoomScheduleChangedEvent(
        UUID roomId,
        Long actorUserId,
        RoomScheduleEventType type,
        Long scheduleId,
        Long itemId
) {
}
