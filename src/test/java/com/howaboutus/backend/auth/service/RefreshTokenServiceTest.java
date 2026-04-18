package com.howaboutus.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.howaboutus.backend.common.config.properties.RefreshTokenProperties;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;

class RefreshTokenServiceTest {

    private RefreshTokenService refreshTokenService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SetOperations<String, String> setOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOperations = Mockito.mock(ValueOperations.class);
        setOperations = Mockito.mock(SetOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.opsForSet()).willReturn(setOperations);

        RefreshTokenProperties properties = new RefreshTokenProperties(1209600000L);
        refreshTokenService = new RefreshTokenService(redisTemplate, properties);
    }

    @Test
    @DisplayName("Refresh Token 생성 시 Redis에 토큰과 유저 Set을 저장한다")
    void createRefreshToken() {
        Long userId = 1L;

        String token = refreshTokenService.create(userId);

        assertThat(token).startsWith("1:");
        String uuid = token.substring(2);
        verify(valueOperations).set(
                eq("refresh:token:" + uuid),
                eq("1"),
                eq(Duration.ofMillis(1209600000L))
        );
        verify(setOperations).add("refresh:user:1", uuid);
        verify(redisTemplate).expire(
                eq("refresh:user:1"),
                eq(Duration.ofMillis(1209600000L))
        );
    }

    @Test
    @DisplayName("유효한 Refresh Token으로 Rotation하면 새 토큰을 발급하고 이전 토큰을 삭제한다")
    void rotateValidToken() {
        String oldUuid = "old-uuid";
        String oldToken = "1:" + oldUuid;
        given(valueOperations.get("refresh:token:old-uuid")).willReturn("1");
        given(redisTemplate.delete("refresh:token:old-uuid")).willReturn(true);

        String newToken = refreshTokenService.rotate(oldToken);

        assertThat(newToken).isNotBlank().isNotEqualTo(oldToken).startsWith("1:");
        verify(redisTemplate).delete("refresh:token:old-uuid");
        verify(setOperations).remove("refresh:user:1", oldUuid);
        verify(valueOperations).set(
                eq("refresh:used:old-uuid"),
                eq("1"),
                eq(Duration.ofMinutes(5))
        );
    }

    @Test
    @DisplayName("만료된 토큰으로 Rotation하면 REFRESH_TOKEN_NOT_FOUND 예외를 던진다")
    void throwsWhenTokenExpired() {
        String token = "1:expired-uuid";
        given(valueOperations.get("refresh:token:expired-uuid")).willReturn(null);
        given(redisTemplate.hasKey("refresh:used:expired-uuid")).willReturn(false); // Set isMember 대신 hasKey

        assertThatThrownBy(() -> refreshTokenService.rotate(token))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 사용된 토큰이 재사용되면 해당 유저의 모든 토큰을 무효화한다")
    void detectsTokenReuseAndInvalidatesAll() {
        String reusedUuid = "reused-uuid";
        String activeUuid = "active-uuid";
        String token = "1:" + reusedUuid;

        given(valueOperations.get("refresh:token:reused-uuid")).willReturn(null);
        given(redisTemplate.hasKey("refresh:used:reused-uuid")).willReturn(true); // used 마커 있음
        given(setOperations.members("refresh:user:1")).willReturn(Set.of(reusedUuid, activeUuid));
        given(redisTemplate.delete("refresh:token:reused-uuid")).willReturn(true);
        given(redisTemplate.delete("refresh:token:active-uuid")).willReturn(true);
        given(redisTemplate.delete("refresh:user:1")).willReturn(true);

        assertThatThrownBy(() -> refreshTokenService.rotate(token))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);

        verify(redisTemplate).delete("refresh:token:reused-uuid");
        verify(redisTemplate).delete("refresh:token:active-uuid");
        verify(redisTemplate).delete("refresh:user:1");
        verify(redisTemplate, never()).delete("refresh:used:reused-uuid");
    }

    @Test
    @DisplayName("TTL 만료 후 정상 요청은 REUSE_DETECTED가 아닌 REFRESH_TOKEN_NOT_FOUND를 던진다")
    void throwsNotFoundWhenTokenTtlExpiredNaturally() {
        // refresh:token:{uuid} 는 TTL 만료, refresh:used:{uuid} 도 없음 → 정상 만료
        String token = "1:naturally-expired-uuid";
        given(valueOperations.get("refresh:token:naturally-expired-uuid")).willReturn(null);
        given(redisTemplate.hasKey("refresh:used:naturally-expired-uuid")).willReturn(false);

        assertThatThrownBy(() -> refreshTokenService.rotate(token))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    @Test
    @DisplayName("잘못된 형식의 토큰은 REFRESH_TOKEN_NOT_FOUND 예외를 던진다")
    void throwsWhenTokenFormatInvalid() {
        assertThatThrownBy(() -> refreshTokenService.rotate("invalid-no-colon"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    @Test
    @DisplayName("userId가 비어있는 토큰은 REFRESH_TOKEN_NOT_FOUND 예외를 던진다")
    void throwsWhenUserIdEmpty() {
        assertThatThrownBy(() -> refreshTokenService.rotate(":some-uuid"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    @Test
    @DisplayName("uuid가 비어있는 토큰은 REFRESH_TOKEN_NOT_FOUND 예외를 던진다")
    void throwsWhenUuidEmpty() {
        assertThatThrownBy(() -> refreshTokenService.rotate("1:"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    @Test
    @DisplayName("userId가 숫자가 아닌 토큰은 REFRESH_TOKEN_NOT_FOUND 예외를 던진다")
    void throwsWhenUserIdNotNumeric() {
        assertThatThrownBy(() -> refreshTokenService.rotate("abc:some-uuid"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    @Test
    @DisplayName("로그아웃 시 해당 Refresh Token만 Redis에서 삭제한다")
    void deletesSingleToken() {
        String uuid = "target-uuid";
        String token = "1:" + uuid;
        given(valueOperations.get("refresh:token:target-uuid")).willReturn("1");
        given(redisTemplate.delete("refresh:token:target-uuid")).willReturn(true);

        refreshTokenService.delete(token);

        verify(redisTemplate).delete("refresh:token:target-uuid");
        verify(setOperations).remove("refresh:user:1", uuid);
    }

    @Test
    @DisplayName("로그아웃 시 이미 만료된 토큰이어도 예외 없이 처리한다")
    void deleteIgnoresExpiredToken() {
        String token = "1:expired-uuid";
        given(valueOperations.get("refresh:token:expired-uuid")).willReturn(null);

        refreshTokenService.delete(token);

        // 예외 없이 정상 종료
    }
}
