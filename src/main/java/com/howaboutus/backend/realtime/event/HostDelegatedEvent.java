package com.howaboutus.backend.realtime.event;

import java.util.UUID;

public record HostDelegatedEvent(
        UUID roomId,
        long previousHostUserId,
        String previousHostNickname,
        long newHostUserId,
        String newHostNickname
) {}
