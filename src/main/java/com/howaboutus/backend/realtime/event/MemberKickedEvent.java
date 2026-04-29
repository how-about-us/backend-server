package com.howaboutus.backend.realtime.event;

import java.util.UUID;

public record MemberKickedEvent(
        UUID roomId,
        long kickedUserId,
        String nickname,
        String profileImageUrl
) {}
