package com.howaboutus.backend.rooms.controller.dto;

import com.howaboutus.backend.rooms.service.dto.JoinStatusResult;
import java.util.UUID;

public record JoinStatusResponse(
        String status,
        UUID id,
        String roomTitle,
        String role
) {
    public static JoinStatusResponse from(JoinStatusResult result) {
        String roleName = null;
        if (result.role() != null) {
            roleName = result.role().name();
        }
        return new JoinStatusResponse(
                result.status().name().toLowerCase(),
                result.roomId(),
                result.roomTitle(),
                roleName);
    }
}
