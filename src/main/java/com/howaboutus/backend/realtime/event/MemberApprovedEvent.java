package com.howaboutus.backend.realtime.event;

import java.util.UUID;

public record MemberApprovedEvent(
        UUID roomId,
        long joinedUserId,
        String nickname,
        String profileImageUrl
) {}
