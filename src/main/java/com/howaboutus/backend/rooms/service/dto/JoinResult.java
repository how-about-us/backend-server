package com.howaboutus.backend.rooms.service.dto;

import com.howaboutus.backend.rooms.entity.RoomRole;
import java.util.UUID;

public record JoinResult(
        JoinStatus status,
        UUID roomId,
        String roomTitle,
        RoomRole role
) {
    public static JoinResult alreadyMember(UUID roomId, String title, RoomRole role) {
        return new JoinResult(JoinStatus.ALREADY_MEMBER, roomId, title, role);
    }

    public static JoinResult pending(String title) {
        return new JoinResult(JoinStatus.PENDING, null, title, null);
    }
}
