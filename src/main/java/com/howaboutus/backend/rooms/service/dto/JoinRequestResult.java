package com.howaboutus.backend.rooms.service.dto;

import java.time.Instant;

public record JoinRequestResult(
        Long requestId,
        Long userId,
        String nickname,
        String profileImageUrl,
        Instant requestedAt
) {
}
