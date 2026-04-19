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
    private static final String USED_KEY_PREFIX = "refresh:used:";
    // rotate 직후 재사용 감지 창. 5분 이후 재사용 시 NOT_FOUND로 거부되며 공격자 진입은 불가하나,
    // invalidateAll(다른 기기 강제 로그아웃)은 발동되지 않음. 의도된 트레이드오프.
    private static final Duration USED_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final RefreshTokenProperties properties;

    public String create(Long userId) {
        String uuid = UUID.randomUUID().toString();
        String token = userId + ":" + uuid;
        String tokenKey = TOKEN_KEY_PREFIX + uuid;
        String userKey = USER_KEY_PREFIX + userId;

        redisTemplate.opsForValue().set(tokenKey, String.valueOf(userId), Duration.ofMillis(properties.expiration()));
        redisTemplate.opsForSet().add(userKey, uuid);
        redisTemplate.expire(userKey, Duration.ofMillis(properties.expiration()));

        return token;
    }

    public RotateResult rotate(String token) {
        TokenParts parts = parseToken(token);

        String tokenKey = TOKEN_KEY_PREFIX + parts.uuid();
        // getAndDelete: GET+DELETE 원자 실행(GETDEL). 동시 요청이 들어와도 한 쪽만 userId를 얻어 새 토큰을 발급한다.
        String userId = redisTemplate.opsForValue().getAndDelete(tokenKey);
        //redis에서 조회했으나, uuid에 해당하는 userid==null 이면, 이미 사용된토큰 또는 위조된 토큰
        if (userId == null) {
            handleMissingToken(parts);
            return null; // handleMissingToken이 항상 에러를 던져서 이 줄은 실행 안됨.
        }
        //null이 아니라면, 기존의 RTK는 폐기되었으므로 새로운 토큰을 발급한다.
        String userKey = USER_KEY_PREFIX + userId;
        redisTemplate.opsForSet().remove(userKey, parts.uuid());
        redisTemplate.opsForValue().set(USED_KEY_PREFIX + parts.uuid(), "1", USED_TTL);

        Long userIdLong = Long.valueOf(userId);
        return new RotateResult(create(userIdLong), userIdLong);
    }

    public record RotateResult(String token, Long userId) {
    }

    public void delete(String token) {
        if (!token.contains(":")) {
            return;
        }
        TokenParts parts = parseToken(token);

        String tokenKey = TOKEN_KEY_PREFIX + parts.uuid();
        String storedUserId = redisTemplate.opsForValue().get(tokenKey);

        if (storedUserId != null) {
            redisTemplate.delete(tokenKey);
            redisTemplate.opsForSet().remove(USER_KEY_PREFIX + storedUserId, parts.uuid());
        }
    }

    private void handleMissingToken(TokenParts parts) {
        Boolean isUsed = redisTemplate.hasKey(USED_KEY_PREFIX + parts.uuid());
        if (Boolean.TRUE.equals(isUsed)) {
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
        if (userId.isEmpty() || uuid.isEmpty()) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }
        try {
            Long.parseLong(userId);
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }
        return new TokenParts(userId, uuid);
    }

    private record TokenParts(String userId, String uuid) {
    }
}
