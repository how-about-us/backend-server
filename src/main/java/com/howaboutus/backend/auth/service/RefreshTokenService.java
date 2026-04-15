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
        //redis에서 조회했으나, uuid에 해당하는 userid==null 이면, 이미 사용된토큰 또는 위조된 토큰
        if (userId == null) {
            handleMissingToken(parts);
            return null; // handleMissingToken이 항상 에러를 던져서 이 줄은 실행 안됨.
        }
        //null이 아니라면, 기존의 RTK는 폐기하고, 새로운 토큰을 발급한다.
        redisTemplate.delete(tokenKey);
        String userKey = USER_KEY_PREFIX + userId;
        redisTemplate.opsForSet().remove(userKey, parts.uuid());

        return create(Long.valueOf(userId));
    }

    public void delete(String token) {
        TokenParts parts;
        try {
            parts = parseToken(token);
        } catch (CustomException e) {
            //로그아웃 할때 사용하므로, 에러가 나더라도, 클라이언트의 문제기에, throw error를 하지않고 조용히 처리.
            return;
        }

        String tokenKey = TOKEN_KEY_PREFIX + parts.uuid();
        String storedUserId = redisTemplate.opsForValue().get(tokenKey);

        if (storedUserId != null) {
            redisTemplate.delete(tokenKey);
            redisTemplate.opsForSet().remove(USER_KEY_PREFIX + storedUserId, parts.uuid());
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
