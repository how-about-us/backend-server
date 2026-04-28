package com.howaboutus.backend.realtime.event;

import com.howaboutus.backend.realtime.service.dto.RoomBookmarkEventType;
import java.util.UUID;

public record RoomBookmarkChangedEvent(
        UUID roomId,
        Long actorUserId,
        RoomBookmarkEventType type,
        Long bookmarkId,
        Long categoryId
) {
}
