package com.howaboutus.backend.auth.service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import com.howaboutus.backend.common.config.properties.RefreshTokenProperties;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(RefreshTokenProperties.class)
public class RefreshTokenService {

    private static final String TOKEN_KEY_PREFIX = "refresh:token:";
    private static final String USER_KEY_PREFIX = "refresh:user:";

    private final StringRedisTemplate redisTemplate;
    private final RefreshTokenProperties properties;

    public String create(Long userId) {
        String uuid = UUID.randomUUID().toString();
        String token = userId + ":" + uuid;
        String tokenKey = TOKEN_KEY_PREFIX + uuid;
        String userKey = USER_KEY_PREFIX + userId;

        redisTemplate.opsForValue().set(tokenKey, String.valueOf(userId), Duration.ofMillis(properties.expiration()));
        redisTemplate.opsForSet().add(userKey, uuid);

        return token;
    }

    public String rotate(String token) {
        TokenParts parts = parseToken(token);

        String tokenKey = TOKEN_KEY_PREFIX + parts.uuid();
        String userId = redisTemplate.opsForValue().get(tokenKey);

        if (userId == null) {
            handleMissingToken(parts);
            return null; // unreachable — handleMissingToken always throws
        }

        redisTemplate.delete(tokenKey);
        String userKey = USER_KEY_PREFIX + userId;
        redisTemplate.opsForSet().remove(userKey, parts.uuid());

        return create(Long.valueOf(userId));
    }

    public Long resolveUserId(String token) {
        TokenParts parts = parseToken(token);
        String tokenKey = TOKEN_KEY_PREFIX + parts.uuid();
        String userId = redisTemplate.opsForValue().get(tokenKey);
        if (userId == null) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }
        return Long.valueOf(userId);
    }

    public void delete(String token) {
        int separatorIndex = token.indexOf(':');
        if (separatorIndex == -1) {
            return;
        }
        String userId = token.substring(0, separatorIndex);
        String uuid = token.substring(separatorIndex + 1);

        String tokenKey = TOKEN_KEY_PREFIX + uuid;
        String storedUserId = redisTemplate.opsForValue().get(tokenKey);

        if (storedUserId != null) {
            redisTemplate.delete(tokenKey);
            redisTemplate.opsForSet().remove(USER_KEY_PREFIX + storedUserId, uuid);
        }
    }

    private void handleMissingToken(TokenParts parts) {
        String userKey = USER_KEY_PREFIX + parts.userId();
        Boolean isMember = redisTemplate.opsForSet().isMember(userKey, parts.uuid());
        if (Boolean.TRUE.equals(isMember)) {
            invalidateAllTokens(parts.userId());
            throw new CustomException(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        }
        throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    private void invalidateAllTokens(String userId) {
        String userKey = USER_KEY_PREFIX + userId;
        Set<String> tokens = redisTemplate.opsForSet().members(userKey);
        if (tokens != null) {
            for (String uuid : tokens) {
                redisTemplate.delete(TOKEN_KEY_PREFIX + uuid);
            }
        }
        redisTemplate.delete(userKey);
    }

    private TokenParts parseToken(String token) {
        int separatorIndex = token.indexOf(':');
        if (separatorIndex == -1) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }
        String userId = token.substring(0, separatorIndex);
        String uuid = token.substring(separatorIndex + 1);
        return new TokenParts(userId, uuid);
    }

    private record TokenParts(String userId, String uuid) {
    }
}
