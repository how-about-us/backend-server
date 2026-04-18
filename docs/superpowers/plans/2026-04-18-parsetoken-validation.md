# `parseToken()` 입력 검증 강화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `parseToken()`이 `:` 존재 여부만 확인하던 것을 userId 빈값, uuid 빈값, userId 비숫자 세 가지 케이스까지 명시적으로 검증해 `REFRESH_TOKEN_NOT_FOUND`로 거부하도록 강화한다.

**Architecture:** `RefreshTokenService.parseToken()` 메서드에만 검증 로직을 추가한다. `TokenParts` 레코드, `rotate()`, `delete()`, `handleMissingToken()`, `invalidateAllTokens()`는 변경하지 않는다. TDD 순서(테스트 먼저 → 구현)로 진행한다.

**Tech Stack:** Spring Boot, Spring Data Redis (`StringRedisTemplate`), JUnit 5, Mockito, AssertJ

---

### Task 1: 새 검증 케이스 3종 테스트 추가 및 구현

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/auth/service/RefreshTokenServiceTest.java`
- Modify: `src/main/java/com/howaboutus/backend/auth/service/RefreshTokenService.java`

- [ ] **Step 1: 테스트 3개 추가**

`RefreshTokenServiceTest.java`의 기존 `throwsWhenTokenFormatInvalid` 테스트 바로 아래에 다음 세 테스트를 추가한다.

```java
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
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest.throwsWhenUserIdEmpty" \
               --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest.throwsWhenUuidEmpty" \
               --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest.throwsWhenUserIdNotNumeric" 2>&1 | tail -30
```

Expected: `throwsWhenUserIdEmpty`와 `throwsWhenUserIdNotNumeric`은 FAIL (현재 검증 없이 Redis 조회로 넘어감), `throwsWhenUuidEmpty`는 현재 동작에 따라 PASS 또는 FAIL.

- [ ] **Step 3: `parseToken()` 검증 추가**

`RefreshTokenService.java`의 `parseToken()` 메서드를 아래로 교체한다.

```java
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
```

- [ ] **Step 4: 신규 테스트 3개 통과 확인**

```bash
./gradlew test --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest.throwsWhenUserIdEmpty" \
               --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest.throwsWhenUuidEmpty" \
               --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest.throwsWhenUserIdNotNumeric" 2>&1 | tail -20
```

Expected: 3개 모두 PASS

- [ ] **Step 5: 전체 테스트 통과 확인**

기존 테스트 회귀가 없는지 확인한다.

```bash
./gradlew test --tests "com.howaboutus.backend.auth.service.RefreshTokenServiceTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, 모든 테스트 PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/auth/service/RefreshTokenService.java \
        src/test/java/com/howaboutus/backend/auth/service/RefreshTokenServiceTest.java
git commit -m "fix: parseToken()에 userId/uuid 빈값 및 비숫자 검증 추가"
```
