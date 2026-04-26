package com.howaboutus.backend.realtime.service;

import java.util.Set;
import java.util.UUID;

public interface RoomPresenceService {

    boolean connect(UUID roomId, Long userId, String sessionId);

    boolean disconnect(UUID roomId, Long userId, String sessionId);

    Set<Long> getOnlineUserIds(UUID roomId);
}
