# Refresh Token Redis 연동 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Access Token 만료 시 Refresh Token(UUID, Redis 저장)으로 재발급하는 Silent Refresh + Rotation + Replay Detection 구현

**Architecture:** UUID opaque Refresh Token을 Redis에 두 가지 키(`refresh:token:{uuid}` → userId, `refresh:user:{userId}` → Set\<uuid\>)로 저장. 매 Refresh마다 Rotation하고, 사용 완료된 토큰 재사용 시 해당 유저 전체 토큰 무효화. 로그아웃은 단일 기기(해당 토큰만 삭제).

**Tech Stack:** Spring Boot 4.0.5, Java 21, Spring Data Redis, StringRedisTemplate, JJWT 0.12.6

**Spec:** `docs/superpowers/specs/2026-04-14-refresh-token-redis-design.md`

---

### Task 1: RefreshTokenProperties 설정

**Files:**
- Create: `src/main/java/com/howaboutus/backend/common/config/properties/RefreshTokenProperties.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/test/resources/application-test.yaml`

- [ ] **Step 1: RefreshTokenProperties record 생성**

```java
package com.howaboutus.backend.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "refresh-token")
public record RefreshTokenProperties(
        long expiration
) {
}
```

- [ ] **Step 2: application.yaml에 refresh-token 설정 추가**

`application.yaml` 파일 맨 끝에 추가:

```yaml
refresh-token:
  expiration: 1209600000  # 14일 (밀리초)
```

- [ ] **Step 3: application-test.yaml에 refresh-token 설정 추가**

`application-test.yaml` 파일 맨 끝에 추가:

```yaml
refresh-token:
  expiration: 3600000  # 테스트용 1시간
```

- [ ] **Step 4: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/config/properties/RefreshTokenProperties.java src/main/resources/application.yaml src/test/resources/application-test.yaml
git commit -m "feat : RefreshTokenProperties 설정 추가"
```

---

### Task 2: ErrorCode 추가

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/common/error/ErrorCode.java`

- [ ] **Step 1: Refresh Token 관련 에러 코드 추가**

`ErrorCode.java`의 `// 401 UNAUTHORIZED` 섹션에 추가:

```java
// 401 UNAUTHORIZED
GOOGLE_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "Google 인증에 실패했습니다"),
INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),
REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 존재하지 않습니다"),
REFRESH_TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "토큰 재사용이 감지되었습니다"),
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/error/ErrorCode.java
git commit -m "feat : Refresh Token 관련 에러 코드 추가"
```

---

### Task 3: RefreshTokenService — 생성/검증/삭제 로직

**Files:**
- Create: `src/main/java/com/howaboutus/backend/auth/service/RefreshTokenService.java`
- Create: `src/test/java/com/howaboutus/backend/auth/service/RefreshTokenServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성 — 토큰 생성**

```java
package com.howaboutus.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.howaboutus.backend.common.config.properties.RefreshTokenProperties;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;

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

        assertThat(token).isNotBlank();
        verify(valueOperations).set(
                eq("refresh:token:" + token),
                eq("1"),
                eq(Duration.ofMillis(1209600000L))
        );
        verify(setOperations).add("refresh:user:1", token);
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest.createRefreshToken"`
Expected: FAIL — `RefreshTokenService` 클래스가 존재하지 않음

- [ ] **Step 3: RefreshTokenService 구현 — create 메서드**

```java
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
        String token = UUID.randomUUID().toString();
        String tokenKey = TOKEN_KEY_PREFIX + token;
        String userKey = USER_KEY_PREFIX + userId;

        redisTemplate.opsForValue().set(tokenKey, String.valueOf(userId), Duration.ofMillis(properties.expiration()));
        redisTemplate.opsForSet().add(userKey, token);

        return token;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest.createRefreshToken"`
Expected: PASS

- [ ] **Step 5: 실패하는 테스트 작성 — 토큰 검증 (정상)**

`RefreshTokenServiceTest`에 추가:

```java
@Test
@DisplayName("유효한 Refresh Token으로 Rotation하면 새 토큰을 발급하고 이전 토큰을 삭제한다")
void rotateValidToken() {
    String oldToken = "old-uuid";
    given(valueOperations.get("refresh:token:old-uuid")).willReturn("1");
    given(redisTemplate.delete("refresh:token:old-uuid")).willReturn(true);

    String newToken = refreshTokenService.rotate(oldToken);

    assertThat(newToken).isNotBlank().isNotEqualTo(oldToken);
    verify(redisTemplate).delete("refresh:token:old-uuid");
    verify(setOperations).remove("refresh:user:1", oldToken);
    verify(valueOperations).set(
            eq("refresh:token:" + newToken),
            eq("1"),
            eq(Duration.ofMillis(1209600000L))
    );
    verify(setOperations).add("refresh:user:1", newToken);
}
```

- [ ] **Step 6: 테스트 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest.rotateValidToken"`
Expected: FAIL — `rotate` 메서드가 존재하지 않음

- [ ] **Step 7: rotate 메서드 구현**

`RefreshTokenService`에 추가:

```java
public String rotate(String oldToken) {
    String tokenKey = TOKEN_KEY_PREFIX + oldToken;
    String userId = redisTemplate.opsForValue().get(tokenKey);

    if (userId == null) {
        handleMissingToken(oldToken);
    }

    redisTemplate.delete(tokenKey);
    String userKey = USER_KEY_PREFIX + userId;
    redisTemplate.opsForSet().remove(userKey, oldToken);

    return create(Long.valueOf(userId));
}

private void handleMissingToken(String token) {
    // Replay Detection은 다음 Step에서 구현
    throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
}
```

- [ ] **Step 8: 테스트 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest.rotateValidToken"`
Expected: PASS

- [ ] **Step 9: 실패하는 테스트 작성 — Replay Detection**

`RefreshTokenServiceTest`에 추가:

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
```

```java
@Test
@DisplayName("이미 사용된 토큰이 재사용되면 해당 유저의 모든 토큰을 무효화하고 예외를 던진다")
void detectsTokenReuse() {
    String reusedToken = "reused-uuid";
    // 토큰 키는 이미 삭제됨 (이전 Rotation에서)
    given(valueOperations.get("refresh:token:reused-uuid")).willReturn(null);
    // 하지만 유저의 Set에는 아직 남아있음 → 탈취 의심
    // findUserIdByToken: 모든 user Set을 순회하는 대신, 토큰 값에 userId를 포함시키는 방법 사용
    // 여기서는 Set에서 찾을 수 없으므로 단순 NOT_FOUND
    assertThatThrownBy(() -> refreshTokenService.rotate(reusedToken))
            .isInstanceOf(CustomException.class)
            .extracting(e -> ((CustomException) e).getErrorCode())
            .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
}
```

- [ ] **Step 10: 테스트 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest.detectsTokenReuse"`
Expected: PASS (기존 handleMissingToken 로직으로 통과)

- [ ] **Step 11: Replay Detection 강화 — 토큰에 userId 포함하는 방식으로 변경**

Replay Detection을 효과적으로 구현하려면 만료된 토큰에서도 userId를 알 수 있어야 한다. 토큰 형식을 `{userId}:{uuid}` 로 변경한다.

`RefreshTokenService`의 `create` 메서드와 `rotate` 메서드를 수정:

```java
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
        // handleMissingToken always throws
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

private void handleMissingToken(TokenParts parts) {
    String userKey = USER_KEY_PREFIX + parts.userId();
    Boolean isMember = redisTemplate.opsForSet().isMember(userKey, parts.uuid());
    if (Boolean.TRUE.equals(isMember)) {
        // 이미 사용된 토큰이 재사용됨 → 탈취 의심 → 전체 무효화
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
```

- [ ] **Step 12: 테스트 전체 수정 — 토큰 형식 변경 반영**

`RefreshTokenServiceTest` 전체를 아래로 교체:

```java
package com.howaboutus.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
    }

    @Test
    @DisplayName("만료된 토큰으로 Rotation하면 REFRESH_TOKEN_NOT_FOUND 예외를 던진다")
    void throwsWhenTokenExpired() {
        String token = "1:expired-uuid";
        given(valueOperations.get("refresh:token:expired-uuid")).willReturn(null);
        given(setOperations.isMember("refresh:user:1", "expired-uuid")).willReturn(false);

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
        given(setOperations.isMember("refresh:user:1", reusedUuid)).willReturn(true);
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
    }

    @Test
    @DisplayName("잘못된 형식의 토큰은 REFRESH_TOKEN_NOT_FOUND 예외를 던진다")
    void throwsWhenTokenFormatInvalid() {
        assertThatThrownBy(() -> refreshTokenService.rotate("invalid-no-colon"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }
}
```

- [ ] **Step 13: 전체 테스트 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest"`
Expected: 5 tests PASS

- [ ] **Step 14: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/auth/service/RefreshTokenService.java src/test/java/com/howaboutus/backend/auth/service/RefreshTokenServiceTest.java
git commit -m "feat : RefreshTokenService 생성/검증/Rotation/Replay Detection 구현"
```

---

### Task 4: RefreshTokenService — delete (로그아웃)

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/auth/service/RefreshTokenService.java`
- Modify: `src/test/java/com/howaboutus/backend/auth/service/RefreshTokenServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성 — 토큰 삭제**

`RefreshTokenServiceTest`에 추가:

```java
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
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest.deletesSingleToken"`
Expected: FAIL — `delete` 메서드가 존재하지 않음

- [ ] **Step 3: delete 메서드 구현**

`RefreshTokenService`에 추가:

```java
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
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest"`
Expected: 7 tests PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/auth/service/RefreshTokenService.java src/test/java/com/howaboutus/backend/auth/service/RefreshTokenServiceTest.java
git commit -m "feat : RefreshTokenService 로그아웃(단일 토큰 삭제) 구현"
```

---

### Task 5: AuthService 수정 — Refresh Token 연동

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/auth/service/AuthService.java`
- Modify: `src/test/java/com/howaboutus/backend/auth/service/AuthServiceTest.java`

- [ ] **Step 1: AuthService 반환 타입 변경을 위한 DTO 생성**

로그인 결과로 accessToken과 refreshToken을 함께 반환해야 한다.

```java
// AuthService.java 내부에 쓸 DTO를 auth/service/dto 패키지에 생성
```

Create: `src/main/java/com/howaboutus/backend/auth/service/dto/LoginResult.java`

```java
package com.howaboutus.backend.auth.service.dto;

public record LoginResult(
        String accessToken,
        String refreshToken,
        Long userId
) {
}
```

- [ ] **Step 2: 실패하는 테스트 수정 — AuthServiceTest 수정**

`AuthServiceTest`를 수정하여 `LoginResult` 반환을 검증:

```java
package com.howaboutus.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.howaboutus.backend.auth.entity.User;
import com.howaboutus.backend.auth.repository.UserRepository;
import com.howaboutus.backend.auth.service.dto.GoogleUserInfo;
import com.howaboutus.backend.auth.service.dto.LoginResult;
import java.util.Optional;

import com.howaboutus.backend.common.integration.google.GoogleOAuthClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AuthServiceTest {

    private AuthService authService;
    private GoogleOAuthClient googleOAuthClient;
    private UserRepository userRepository;
    private JwtProvider jwtProvider;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        googleOAuthClient = Mockito.mock(GoogleOAuthClient.class);
        userRepository = Mockito.mock(UserRepository.class);
        jwtProvider = Mockito.mock(JwtProvider.class);
        refreshTokenService = Mockito.mock(RefreshTokenService.class);
        authService = new AuthService(googleOAuthClient, userRepository, jwtProvider, refreshTokenService);
    }

    @Test
    @DisplayName("신규 사용자 로그인 시 회원가입 후 Access Token과 Refresh Token을 발급한다")
    void registersNewUserAndReturnsTokens() {
        GoogleUserInfo userInfo = new GoogleUserInfo("google-123", "test@gmail.com", "테스트", null);
        given(googleOAuthClient.login("auth-code")).willReturn(userInfo);
        given(userRepository.findByProviderAndProviderId("GOOGLE", "google-123")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(jwtProvider.generateAccessToken(any())).willReturn("jwt-token");
        given(refreshTokenService.create(any())).willReturn("1:refresh-uuid");

        LoginResult result = authService.googleLogin("auth-code");

        assertThat(result.accessToken()).isEqualTo("jwt-token");
        assertThat(result.refreshToken()).isEqualTo("1:refresh-uuid");
    }

    @Test
    @DisplayName("기존 사용자 로그인 시 조회 후 Access Token과 Refresh Token을 발급한다")
    void returnsTokensForExistingUser() {
        GoogleUserInfo userInfo = new GoogleUserInfo("google-123", "test@gmail.com", "테스트", null);
        User existingUser = User.ofGoogle("google-123", "test@gmail.com", "테스트", null);

        given(googleOAuthClient.login("auth-code")).willReturn(userInfo);
        given(userRepository.findByProviderAndProviderId("GOOGLE", "google-123"))
                .willReturn(Optional.of(existingUser));
        given(jwtProvider.generateAccessToken(any())).willReturn("jwt-token");
        given(refreshTokenService.create(any())).willReturn("1:refresh-uuid");

        LoginResult result = authService.googleLogin("auth-code");

        assertThat(result.accessToken()).isEqualTo("jwt-token");
        assertThat(result.refreshToken()).isEqualTo("1:refresh-uuid");
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.auth.service.AuthServiceTest"`
Expected: FAIL — `AuthService`가 아직 `String`을 반환

- [ ] **Step 4: AuthService 수정**

```java
package com.howaboutus.backend.auth.service;

import com.howaboutus.backend.auth.entity.User;
import com.howaboutus.backend.auth.repository.UserRepository;
import com.howaboutus.backend.auth.service.dto.GoogleUserInfo;
import com.howaboutus.backend.auth.service.dto.LoginResult;
import com.howaboutus.backend.common.integration.google.GoogleOAuthClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleOAuthClient googleOAuthClient;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public LoginResult googleLogin(String authorizationCode) {
        GoogleUserInfo userInfo = googleOAuthClient.login(authorizationCode);

        User user = userRepository.findByProviderAndProviderId("GOOGLE", userInfo.providerId())
                .orElseGet(() -> userRepository.save(
                        User.ofGoogle(
                                userInfo.providerId(),
                                userInfo.email(),
                                userInfo.nickname(),
                                userInfo.profileImageUrl()
                        )
                ));

        String accessToken = jwtProvider.generateAccessToken(user.getId());
        String refreshToken = refreshTokenService.create(user.getId());

        return new LoginResult(accessToken, refreshToken, user.getId());
    }

    public LoginResult refresh(String refreshToken) {
        String newRefreshToken = refreshTokenService.rotate(refreshToken);
        // rotate가 성공했으면 토큰 형식에서 userId 추출
        Long userId = Long.valueOf(newRefreshToken.substring(0, newRefreshToken.indexOf(':')));
        String accessToken = jwtProvider.generateAccessToken(userId);

        return new LoginResult(accessToken, newRefreshToken, userId);
    }

    public void logout(String refreshToken) {
        refreshTokenService.delete(refreshToken);
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.auth.service.AuthServiceTest"`
Expected: 2 tests PASS

- [ ] **Step 6: Refresh/Logout 테스트 추가**

`AuthServiceTest`에 추가:

```java
import static org.mockito.Mockito.verify;

@Test
@DisplayName("Refresh Token으로 새 토큰 쌍을 발급한다")
void refreshReturnsNewTokens() {
    given(refreshTokenService.rotate("1:old-uuid")).willReturn("1:new-uuid");
    given(jwtProvider.generateAccessToken(1L)).willReturn("new-jwt");

    LoginResult result = authService.refresh("1:old-uuid");

    assertThat(result.accessToken()).isEqualTo("new-jwt");
    assertThat(result.refreshToken()).isEqualTo("1:new-uuid");
}

@Test
@DisplayName("로그아웃 시 RefreshTokenService.delete를 호출한다")
void logoutDeletesToken() {
    authService.logout("1:some-uuid");

    verify(refreshTokenService).delete("1:some-uuid");
}
```

- [ ] **Step 7: 전체 테스트 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.auth.service.AuthServiceTest"`
Expected: 4 tests PASS

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/auth/service/dto/LoginResult.java src/main/java/com/howaboutus/backend/auth/service/AuthService.java src/test/java/com/howaboutus/backend/auth/service/AuthServiceTest.java
git commit -m "feat : AuthService에 Refresh Token 생성/갱신/로그아웃 연동"
```

---

### Task 6: AuthController 수정 — 엔드포인트 추가 & 쿠키 처리

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/auth/controller/AuthController.java`
- Modify: `src/test/java/com/howaboutus/backend/auth/controller/AuthControllerTest.java`

- [ ] **Step 1: 실패하는 테스트 수정 — 기존 로그인 테스트 수정 + 신규 테스트 추가**

`AuthControllerTest`를 아래로 교체:

```java
package com.howaboutus.backend.auth.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.auth.service.AuthService;
import com.howaboutus.backend.auth.service.dto.LoginResult;
import com.howaboutus.backend.common.config.SecurityConfig;
import com.howaboutus.backend.common.config.properties.JwtProperties;
import com.howaboutus.backend.common.config.properties.RefreshTokenProperties;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @MockitoBean
    private RefreshTokenProperties refreshTokenProperties;

    @Test
    @DisplayName("Google 로그인 성공 시 access_token과 refresh_token 쿠키를 반환한다")
    void returnsAccessAndRefreshTokenCookiesOnLogin() throws Exception {
        given(authService.googleLogin("valid-code"))
                .willReturn(new LoginResult("jwt-token", "1:refresh-uuid", 1L));
        given(jwtProperties.accessTokenExpiration()).willReturn(1800000L);
        given(refreshTokenProperties.expiration()).willReturn(1209600000L);

        mockMvc.perform(post("/auth/google/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "valid-code"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true));
    }

    @Test
    @DisplayName("Google 인증 실패 시 401을 반환한다")
    void returns401WhenGoogleAuthFails() throws Exception {
        given(authService.googleLogin("bad-code"))
                .willThrow(new CustomException(ErrorCode.GOOGLE_AUTH_FAILED));

        mockMvc.perform(post("/auth/google/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "bad-code"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Refresh 요청 시 새 토큰 쿠키를 반환한다")
    void refreshReturnsNewCookies() throws Exception {
        given(authService.refresh("1:old-uuid"))
                .willReturn(new LoginResult("new-jwt", "1:new-uuid", 1L));
        given(jwtProperties.accessTokenExpiration()).willReturn(1800000L);
        given(refreshTokenProperties.expiration()).willReturn(1209600000L);

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", "1:old-uuid")))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"));
    }

    @Test
    @DisplayName("Refresh Token 쿠키 없이 Refresh 요청 시 401을 반환한다")
    void returns401WhenRefreshTokenCookieMissing() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃 시 쿠키를 삭제하고 204를 반환한다")
    void logoutDeletesCookiesAndReturns204() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie("refresh_token", "1:some-uuid")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("access_token", 0))
                .andExpect(cookie().maxAge("refresh_token", 0));

        verify(authService).logout("1:some-uuid");
    }

    @Test
    @DisplayName("Refresh Token 쿠키 없이 로그아웃 요청 시에도 204를 반환한다")
    void logoutWithoutCookieReturns204() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.auth.controller.AuthControllerTest"`
Expected: FAIL — `AuthController`가 아직 업데이트되지 않음

- [ ] **Step 3: AuthController 수정**

```java
package com.howaboutus.backend.auth.controller;

import com.howaboutus.backend.auth.controller.dto.GoogleLoginRequest;
import com.howaboutus.backend.auth.service.AuthService;
import com.howaboutus.backend.auth.service.dto.LoginResult;
import com.howaboutus.backend.common.config.properties.JwtProperties;
import com.howaboutus.backend.common.config.properties.RefreshTokenProperties;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import java.time.Duration;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenProperties refreshTokenProperties;

    @PostMapping("/google/login")
    public ResponseEntity<Void> googleLogin(@RequestBody GoogleLoginRequest request) {
        LoginResult result = authService.googleLogin(request.code());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildAccessTokenCookie(result.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(result.refreshToken()).toString())
                .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        LoginResult result = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildAccessTokenCookie(result.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(result.refreshToken()).toString())
                .build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }

        ResponseCookie expiredAccess = ResponseCookie.from("access_token", "")
                .httpOnly(true).sameSite("Lax").path("/").maxAge(0).build();
        ResponseCookie expiredRefresh = ResponseCookie.from("refresh_token", "")
                .httpOnly(true).sameSite("Lax").path("/auth/refresh").maxAge(0).build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredAccess.toString())
                .header(HttpHeaders.SET_COOKIE, expiredRefresh.toString())
                .build();
    }

    private ResponseCookie buildAccessTokenCookie(String token) {
        return ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMillis(jwtProperties.accessTokenExpiration()))
                .build();
    }

    private ResponseCookie buildRefreshTokenCookie(String token) {
        return ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/auth/refresh")
                .maxAge(Duration.ofMillis(refreshTokenProperties.expiration()))
                .build();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.howaboutus.backend.auth.controller.AuthControllerTest"`
Expected: 6 tests PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/auth/controller/AuthController.java src/test/java/com/howaboutus/backend/auth/controller/AuthControllerTest.java
git commit -m "feat : AuthController에 /auth/refresh, /auth/logout 엔드포인트 추가"
```

---

### Task 7: SecurityConfig 수정

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/common/config/SecurityConfig.java`

- [ ] **Step 1: SecurityConfig에 /auth/refresh, /auth/logout permitAll 추가**

현재 `anyRequest().permitAll()`로 이미 전체 열려 있지만, 나중에 인증을 활성화할 때를 대비해 명시적으로 추가:

```java
.authorizeHttpRequests(authorize -> authorize
        .requestMatchers(
                "/v3/api-docs/**",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/auth/google/login",
                "/auth/refresh",
                "/auth/logout")
        .permitAll()
        // 일단 임시로 인증 열어 놓음
        .anyRequest().permitAll())
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/config/SecurityConfig.java
git commit -m "chore : SecurityConfig에 auth 엔드포인트 permitAll 명시"
```

---

### Task 8: application-prod.yaml Access Token 만료시간 설정

**Files:**
- Modify: `src/main/resources/application-prod.yaml`

- [ ] **Step 1: prod 환경 Access Token 만료시간 30분으로 설정**

`application-prod.yaml`에 추가:

```yaml
jwt:
  access-token-expiration: 1800000  # 30분 (밀리초)
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/resources/application-prod.yaml
git commit -m "chore : prod 환경 Access Token 만료시간 30분으로 설정"
```

---

### Task 9: 전체 빌드 & 테스트 확인

**Files:**
- 없음 (검증만)

- [ ] **Step 1: 전체 테스트 실행**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, 모든 테스트 PASS

- [ ] **Step 2: 실패하는 테스트가 있으면 수정**

기존 테스트가 `AuthService.googleLogin` 반환 타입 변경으로 깨질 수 있음. Task 5에서 이미 수정했으므로 여기서는 확인만.

- [ ] **Step 3: 전체 빌드 확인**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

---

### Task 10: 문서 갱신

**Files:**
- Modify: `docs/ai/features.md`
- Modify: `docs/ai/erd.md`

- [ ] **Step 1: features.md에서 Refresh Token 상태를 완료로 변경**

`docs/ai/features.md`에서 Refresh Token 항목을 `[ ]` → `[x]`로 변경하고, 구현 내용을 간략히 기술.

- [ ] **Step 2: erd.md에 Redis 키 구조 갱신**

`docs/ai/erd.md`의 Redis 키 섹션에 Refresh Token 키 패턴을 확정된 내용으로 업데이트:
- `refresh:token:{uuid}` → userId, TTL 14일
- `refresh:user:{userId}` → Set\<uuid\>, TTL 없음

- [ ] **Step 3: 커밋**

```bash
git add docs/ai/features.md docs/ai/erd.md
git commit -m "docs : Refresh Token Redis 연동 완료 반영"
```
