package com.howaboutus.backend.rooms.service.dto;

import com.howaboutus.backend.rooms.entity.RoomRole;
import java.util.UUID;

public record JoinStatusResult(
        String status,
        UUID roomId,
        String roomTitle,
        RoomRole role
) {
    public static JoinStatusResult approved(UUID roomId, String title, RoomRole role) {
        return new JoinStatusResult("approved", roomId, title, role);
    }

    public static JoinStatusResult pending(String title) {
        return new JoinStatusResult("pending", null, title, null);
    }
}
