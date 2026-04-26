package com.howaboutus.backend.realtime.service;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisRoomPresenceService implements RoomPresenceService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean connect(UUID roomId, Long userId, String sessionId) {
        Long addedUsers = redisTemplate.opsForSet().add(connectedUsersKey(roomId), String.valueOf(userId));
        redisTemplate.opsForSet().add(userSessionsKey(roomId, userId), sessionId);
        return addedUsers != null && addedUsers > 0;
    }

    @Override
    public boolean disconnect(UUID roomId, Long userId, String sessionId) {
        String userSessionsKey = userSessionsKey(roomId, userId);
        redisTemplate.opsForSet().remove(userSessionsKey, sessionId);

        Long remainingSessions = redisTemplate.opsForSet().size(userSessionsKey);
        if (remainingSessions == null || remainingSessions == 0) {
            redisTemplate.opsForSet().remove(connectedUsersKey(roomId), String.valueOf(userId));
            redisTemplate.delete(userSessionsKey);
            return true;
        }
        return false;
    }

    @Override
    public Set<Long> getOnlineUserIds(UUID roomId) {
        Set<String> userIds = redisTemplate.opsForSet().members(connectedUsersKey(roomId));
        if (userIds == null) {
            return Collections.emptySet();
        }
        return userIds.stream()
                .map(Long::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String connectedUsersKey(UUID roomId) {
        return "room:" + roomId + ":connected_users";
    }

    private String userSessionsKey(UUID roomId, Long userId) {
        return "room:" + roomId + ":sessions:" + userId;
    }
}
