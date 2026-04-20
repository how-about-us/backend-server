# `parseToken()` 입력 검증 강화 Design Spec

**Date:** 2026-04-18
**Status:** Approved

---

## 배경

Copilot 코드 리뷰에서 `RefreshTokenService.parseToken()`이 `:` 존재 여부만 확인해, 아래 세 가지 비정상 입력을 그대로 통과시키는 문제를 지적했다.

| 입력 예시 | 현재 동작 | 위험 |
|---|---|---|
| `":uuid"` | userId="" → Redis null → `NOT_FOUND` (우연히 안전) | 낮음 |
| `"1:"` | uuid="" → `refresh:token:` 조회 → `NOT_FOUND` | 낮음 |
| `"abc:uuid"` | UUID가 Redis에 존재하면 `Long.valueOf("abc")` → **NumberFormatException (500)** | 중간 |

쿠키는 클라이언트 입력이므로 파싱 단계에서 명시적으로 거부하는 것이 올바른 설계다.

---

## 변경 대상

- **파일:** `src/main/java/com/howaboutus/backend/auth/service/RefreshTokenService.java`
- **메서드:** `parseToken()` 만 수정

---

## 검증 로직

3단계 검증을 순서대로 적용한다. 모두 `ErrorCode.REFRESH_TOKEN_NOT_FOUND`를 던진다.

1. `separatorIndex == -1` — `:` 자체가 없는 경우 (기존 동작 유지)
2. `userId.isEmpty() || uuid.isEmpty()` — `:uuid` 또는 `1:` 형식
3. `Long.parseLong(userId)` 실패 — `abc:uuid` 형식

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

---

## 에러 처리 방침

세 조건 모두 `REFRESH_TOKEN_NOT_FOUND`로 통일한다. 클라이언트 입장에서 "형식이 잘못됐다"와 "토큰이 없다"를 구분할 필요가 없으며, 상세한 에러 이유를 노출하면 오히려 공격자에게 정보를 제공할 수 있다.

---

## 영향 범위

| 대상 | 변경 여부 |
|---|---|
| `parseToken()` | 수정 |
| `TokenParts` 레코드 | 변경 없음 |
| `rotate()` | 변경 없음 |
| `delete()` | 변경 없음 |
| `handleMissingToken()` | 변경 없음 |
| `invalidateAllTokens()` | 변경 없음 |

---

## 테스트

기존 테스트 `throwsWhenTokenFormatInvalid("invalid-no-colon")`은 이미 통과 중이며 유지한다.

추가할 테스트 케이스 3종:

| 입력 | 기대 예외 |
|---|---|
| `":some-uuid"` | `REFRESH_TOKEN_NOT_FOUND` |
| `"1:"` | `REFRESH_TOKEN_NOT_FOUND` |
| `"abc:some-uuid"` | `REFRESH_TOKEN_NOT_FOUND` |
