package com.howaboutus.backend.rooms.service.dto;

import com.howaboutus.backend.rooms.entity.RoomRole;
import java.time.Instant;

public record RoomMemberResult(
        Long userId,
        String nickname,
        String profileImageUrl,
        RoomRole role,
        boolean isOnline,
        Instant joinedAt
) {
}
