package com.howaboutus.backend.rooms.service.dto;

import com.howaboutus.backend.rooms.entity.RoomRole;
import java.util.UUID;

public record JoinResult(
        String status,
        UUID roomId,
        String roomTitle,
        RoomRole role
) {
    public static JoinResult alreadyMember(UUID roomId, String title, RoomRole role) {
        return new JoinResult("already_member", roomId, title, role);
    }

    public static JoinResult pending(String title) {
        return new JoinResult("pending", null, title, null);
    }
}
