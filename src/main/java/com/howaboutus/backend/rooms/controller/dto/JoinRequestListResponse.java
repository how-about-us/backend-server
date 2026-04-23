package com.howaboutus.backend.rooms.controller.dto;

import com.howaboutus.backend.rooms.service.dto.JoinRequestResult;
import java.time.Instant;
import java.util.List;

public record JoinRequestListResponse(
        List<JoinRequestItem> requests
) {
    public record JoinRequestItem(
            Long requestId,
            Long userId,
            String nickname,
            String profileImageUrl,
            Instant requestedAt
    ) {
    }

    public static JoinRequestListResponse from(List<JoinRequestResult> results) {
        List<JoinRequestItem> items = results.stream()
                .map(r -> new JoinRequestItem(
                        r.requestId(), r.userId(), r.nickname(),
                        r.profileImageUrl(), r.requestedAt()))
                .toList();
        return new JoinRequestListResponse(items);
    }
}
