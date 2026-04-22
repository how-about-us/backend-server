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
        return new JoinResponse(
                result.status(),
                result.roomId(),
                result.roomTitle(),
                result.role() != null ? result.role().name() : null);
    }
}
