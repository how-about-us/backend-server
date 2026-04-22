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
        return new JoinStatusResponse(
                result.status(),
                result.roomId(),
                result.roomTitle(),
                result.role() != null ? result.role().name() : null);
    }
}
