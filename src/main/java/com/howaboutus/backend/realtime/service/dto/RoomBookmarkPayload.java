package com.howaboutus.backend.realtime.service.dto;

import java.util.UUID;

public record RoomBookmarkPayload(
        UUID roomId,
        Long actorUserId,
        RoomBookmarkEventType type,
        Long bookmarkId,
        Long categoryId
) {
}
