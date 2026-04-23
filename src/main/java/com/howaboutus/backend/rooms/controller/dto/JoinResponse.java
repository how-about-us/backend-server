package com.howaboutus.backend.rooms.controller.dto;

import com.howaboutus.backend.rooms.service.dto.JoinResult;
import java.util.UUID;

public record JoinResponse(
        String status,
        UUID id,
        String roomTitle,
        String role
) {
    public static JoinResponse from(JoinResult result) {
        String roleName = null;
        if (result.role() != null) {
            roleName = result.role().name();
        }
        return new JoinResponse(
                result.status().name().toLowerCase(),
                result.roomId(),
                result.roomTitle(),
                roleName);
    }
}
