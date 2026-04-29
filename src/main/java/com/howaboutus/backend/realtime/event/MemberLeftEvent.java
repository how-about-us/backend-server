package com.howaboutus.backend.realtime.event;

import java.util.UUID;

public record MemberLeftEvent(
        UUID roomId,
        long leftUserId,
        String nickname,
        String profileImageUrl
) {}
