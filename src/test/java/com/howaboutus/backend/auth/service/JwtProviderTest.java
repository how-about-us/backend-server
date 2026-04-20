package com.howaboutus.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private JwtProvider jwtProvider;
    private SecretKey secretKey;
    private static final String SECRET = "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha";
    private static final long EXPIRATION = 3600000L;

    @BeforeEach
    void setUp() {
        secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        jwtProvider = new JwtProvider(secretKey, EXPIRATION);
    }

    @Test
    @DisplayName("userId를 subject로 담은 JWT를 생성한다")
    void generatesJwtWithUserIdAsSubject() {
        String token = jwtProvider.generateAccessToken(1L);

        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("1");
    }

    @Test
    @DisplayName("생성된 JWT에 만료 시간이 설정되어 있다")
    void generatesJwtWithExpiration() {
        String token = jwtProvider.generateAccessToken(1L);

        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration().getTime())
                .isGreaterThan(System.currentTimeMillis());
    }

    @Test
    @DisplayName("유효한 토큰에서 userId를 추출한다")
    void extractsUserIdFromValidToken() {
        String token = jwtProvider.generateAccessToken(42L);

        Long userId = jwtProvider.extractUserId(token);

        assertThat(userId).isEqualTo(42L);
    }

    @Test
    @DisplayName("만료된 토큰이면 ACCESS_TOKEN_EXPIRED 예외를 던진다")
    void throwsAccessTokenExpiredForExpiredToken() {
        JwtProvider shortLivedProvider = new JwtProvider(secretKey, 0L);
        String token = shortLivedProvider.generateAccessToken(1L);

        assertThatThrownBy(() -> jwtProvider.extractUserId(token))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ACCESS_TOKEN_EXPIRED));
    }

    @Test
    @DisplayName("위변조된 토큰이면 INVALID_TOKEN 예외를 던진다")
    void throwsInvalidTokenForTamperedToken() {
        assertThatThrownBy(() -> jwtProvider.extractUserId("invalid.token.value"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_TOKEN));
    }
}
