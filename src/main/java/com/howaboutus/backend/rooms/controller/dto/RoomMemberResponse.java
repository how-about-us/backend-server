package com.howaboutus.backend.rooms.controller.dto;

import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.service.dto.RoomMemberResult;
import java.time.Instant;

public record RoomMemberResponse(
        Long userId,
        String nickname,
        String profileImageUrl,
        RoomRole role,
        boolean isOnline,
        Instant joinedAt
) {
    public static RoomMemberResponse from(RoomMemberResult result) {
        return new RoomMemberResponse(
                result.userId(),
                result.nickname(),
                result.profileImageUrl(),
                result.role(),
                result.isOnline(),
                result.joinedAt());
    }
}
