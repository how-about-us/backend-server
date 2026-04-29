package com.howaboutus.backend.realtime.service;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoomPresenceService {

    private final StringRedisTemplate redisTemplate;

    public boolean connect(UUID roomId, Long userId, String sessionId) {
        Long addedUsers = redisTemplate.opsForSet().add(connectedUsersKey(roomId), String.valueOf(userId));
        redisTemplate.opsForSet().add(userSessionsKey(roomId, userId), sessionId);
        return addedUsers != null && addedUsers > 0;
    }

    // TODO: 나중에 Lua Script로 옮기자
    public boolean disconnect(UUID roomId, Long userId, String sessionId) {
        String userSessionsKey = userSessionsKey(roomId, userId);
        Long removedCount = redisTemplate.opsForSet().remove(userSessionsKey, sessionId);
        if (removedCount == null || removedCount == 0) {
            return false;
        }

        Long remainingSessions = redisTemplate.opsForSet().size(userSessionsKey);
        if (remainingSessions == null || remainingSessions == 0) {
            redisTemplate.opsForSet().remove(connectedUsersKey(roomId), String.valueOf(userId));
            redisTemplate.delete(userSessionsKey);
            return true;
        }
        return false;
    }

    public void removeAllSessions(UUID roomId, Long userId) {
        redisTemplate.delete(userSessionsKey(roomId, userId));
        redisTemplate.opsForSet().remove(connectedUsersKey(roomId), String.valueOf(userId));
    }

    public Set<Long> getOnlineUserIds(UUID roomId) {
        Set<String> userIds = redisTemplate.opsForSet().members(connectedUsersKey(roomId));
        if (userIds == null) {
            return Collections.emptySet();
        }
        return userIds.stream()
                .map(this::parseUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Long parseUserId(String raw) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String connectedUsersKey(UUID roomId) {
        return "room:" + roomId + ":connected_users";
    }

    private String userSessionsKey(UUID roomId, Long userId) {
        return "room:" + roomId + ":sessions:" + userId;
    }
}
