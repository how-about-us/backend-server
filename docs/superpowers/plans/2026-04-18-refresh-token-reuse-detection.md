# Refresh Token Reuse Detection 개선 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `refresh:used:{uuid}` 마커 키를 도입해 TTL 만료 직후 정상 요청이 REUSE_DETECTED로 오탐되는 버그를 수정한다.

**Architecture:** rotate() 시 기존 uuid에 대해 `refresh:used:{uuid}` 키를 TTL 5분으로 저장한다. handleMissingToken()은 Set membership 대신 이 마커 키의 존재 여부로 재사용 여부를 판단한다. User Set(`refresh:user:{userId}`)은 invalidateAll 용도로 유지하되, create() 시 TTL을 갱신해 Set이 영구 보존되는 문제를 함께 해결한다.

**Tech Stack:** Spring Boot, Spring Data Redis (`StringRedisTemplate`), JUnit 5, Mockito

---

### Task 1: create() — User Set TTL 갱신 테스트 추가 및 구현

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/auth/service/RefreshTokenServiceTest.java`
- Modify: `src/main/java/com/howaboutus/backend/auth/service/RefreshTokenService.java`

- [ ] **Step 1: 기존 `createRefreshToken` 테스트에 TTL 검증 추가**

`createRefreshToken` 테스트에 아래 `verify`를 추가한다.

```java
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
    verify(redisTemplate).expire(                        // 추가
            eq("refresh:user:1"),                        // 추가
            eq(Duration.ofMillis(1209600000L))           // 추가
    );                                                   // 추가
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest.createRefreshToken" 2>&1 | tail -20
```

Expected: FAIL (Wanted but not invoked: redisTemplate.expire(...))

- [ ] **Step 3: `create()` 에 expire 호출 추가**

`RefreshTokenService.java`의 `create()` 메서드를 아래와 같이 수정한다.

```java
public String create(Long userId) {
    String uuid = UUID.randomUUID().toString();
    String token = userId + ":" + uuid;
    String tokenKey = TOKEN_KEY_PREFIX + uuid;
    String userKey = USER_KEY_PREFIX + userId;

    redisTemplate.opsForValue().set(tokenKey, String.valueOf(userId), Duration.ofMillis(properties.expiration()));
    redisTemplate.opsForSet().add(userKey, uuid);
    redisTemplate.expire(userKey, Duration.ofMillis(properties.expiration())); // 추가

    return token;
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest.createRefreshToken" 2>&1 | tail -20
```

Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/auth/service/RefreshTokenService.java \
        src/test/java/com/howaboutus/backend/auth/service/RefreshTokenServiceTest.java
git commit -m "fix: refresh:user Set에 TTL 갱신 추가"
```

---

### Task 2: rotate() — used 마커 저장 테스트 추가 및 구현

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/auth/service/RefreshTokenServiceTest.java`
- Modify: `src/main/java/com/howaboutus/backend/auth/service/RefreshTokenService.java`

- [ ] **Step 1: `rotateValidToken` 테스트에 used 마커 저장 검증 추가**

```java
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
    verify(valueOperations).set(                          // 추가
            eq("refresh:used:old-uuid"),                  // 추가
            eq("1"),                                      // 추가
            eq(Duration.ofMinutes(5))                     // 추가
    );                                                    // 추가
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest.rotateValidToken" 2>&1 | tail -20
```

Expected: FAIL (Wanted but not invoked: valueOperations.set("refresh:used:old-uuid", ...))

- [ ] **Step 3: 상수 추가 및 `rotate()` 수정**

`RefreshTokenService.java` 상단 상수에 아래 두 줄을 추가하고, `rotate()` 메서드를 수정한다.

```java
// 상수 (기존 상수 아래에 추가)
private static final String USED_KEY_PREFIX = "refresh:used:";
private static final Duration USED_TTL = Duration.ofMinutes(5);
```

```java
public String rotate(String token) {
    TokenParts parts = parseToken(token);

    String tokenKey = TOKEN_KEY_PREFIX + parts.uuid();
    String userId = redisTemplate.opsForValue().get(tokenKey);
    if (userId == null) {
        handleMissingToken(parts);
        return null;
    }
    redisTemplate.delete(tokenKey);
    String userKey = USER_KEY_PREFIX + userId;
    redisTemplate.opsForSet().remove(userKey, parts.uuid());
    redisTemplate.opsForValue().set(USED_KEY_PREFIX + parts.uuid(), "1", USED_TTL); // 추가

    return create(Long.valueOf(userId));
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest.rotateValidToken" 2>&1 | tail -20
```

Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/auth/service/RefreshTokenService.java \
        src/test/java/com/howaboutus/backend/auth/service/RefreshTokenServiceTest.java
git commit -m "fix: rotate 시 refresh:used 마커 키 저장"
```

---

### Task 3: handleMissingToken() — Set membership → used 마커로 교체

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/auth/service/RefreshTokenServiceTest.java`
- Modify: `src/main/java/com/howaboutus/backend/auth/service/RefreshTokenService.java`

- [ ] **Step 1: 기존 테스트 3개 수정 + 신규 테스트 1개 추가**

아래 3개 테스트를 교체하고, 신규 테스트 1개를 추가한다.

```java
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
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest" 2>&1 | tail -30
```

Expected: 일부 FAIL (handleMissingToken이 아직 Set isMember를 사용하므로)

- [ ] **Step 3: `handleMissingToken()` 수정**

```java
private void handleMissingToken(TokenParts parts) {
    Boolean isUsed = redisTemplate.hasKey(USED_KEY_PREFIX + parts.uuid());
    if (Boolean.TRUE.equals(isUsed)) {
        invalidateAllTokens(parts.userId());
        throw new CustomException(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
    }
    throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
}
```

- [ ] **Step 4: 전체 테스트 통과 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest" 2>&1 | tail -30
```

Expected: 모두 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/auth/service/RefreshTokenService.java \
        src/test/java/com/howaboutus/backend/auth/service/RefreshTokenServiceTest.java
git commit -m "fix: handleMissingToken을 Set membership에서 used 마커로 교체"
```

---

### Task 4: 전체 테스트 및 최종 확인

**Files:**
- 변경 없음 (검증만)

- [ ] **Step 1: 전체 테스트 실행**

```bash
./gradlew test 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 최종 상태 확인**

`RefreshTokenService.java`의 최종 상태가 아래와 일치하는지 확인한다.

| 항목 | 기대값 |
|------|--------|
| 상수 | `TOKEN_KEY_PREFIX`, `USER_KEY_PREFIX`, `USED_KEY_PREFIX`, `USED_TTL` 존재 |
| `create()` | `opsForSet().add()` 후 `expire()` 호출 |
| `rotate()` | `opsForSet().remove()` 후 `opsForValue().set(usedKey, "1", USED_TTL)` 호출 |
| `handleMissingToken()` | `redisTemplate.hasKey(usedKey)` 로 판단 |
| `invalidateAllTokens()` | 변경 없음 |
