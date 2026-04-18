# Refresh Token Reuse Detection 설계 개선

Date: 2026-04-18

## 문제

`handleMissingToken()`은 `refresh:token:{uuid}` 키가 Redis에서 조회되지 않을 때, `refresh:user:{userId}` Set에 해당 uuid가 남아 있으면 `REFRESH_TOKEN_REUSE_DETECTED`로 판단하고 전체 무효화를 수행한다.

그러나 이 판단에는 두 가지 오탐 케이스가 존재한다.

### 오탐 케이스 1: TTL 만료 직후 정상 요청

- `refresh:token:{uuid}` 키는 Redis TTL로 자동 만료된다.
- 쿠키의 `maxAge`는 서버가 응답을 보낸 시점이 아닌 브라우저가 수신한 시점부터 카운트되므로, 네트워크 전송 시간만큼 Redis TTL보다 오래 살아있다.
- 결과: 쿠키는 유효한데 Redis에 키가 없는 상황이 발생하고, Set에 uuid가 남아있어 `REUSE_DETECTED` 오탐 발생.

### 오탐 케이스 2: 중복 refresh 요청

- 네트워크 오류로 동일한 refresh 요청이 두 번 전달될 수 있다.
- 첫 번째 요청이 정상 처리되어 기존 uuid가 삭제된 상태에서, 두 번째 요청이 들어오면 Set에 uuid가 남아있어 `REUSE_DETECTED` 오탐 발생.

> 케이스 2는 "네트워크 오류 + 만료 직전 타이밍" 두 조건이 겹쳐야 발생하는 드문 케이스로, 이번 개선 범위에서는 해결하지 않는다.

---

## 해결 방향: `refresh:used:{uuid}` 마커 키 도입

재사용 판단 기준을 **Set membership** 에서 **used 마커 키 존재 여부** 로 교체한다.

---

## Redis 키 구조

| 키 | 역할 | TTL | 변경 여부 |
|----|------|-----|----------|
| `refresh:token:{uuid}` | uuid → userId 매핑, 유효한 토큰 존재 여부 | `refresh-token.expiration` | 유지 |
| `refresh:user:{userId}` | userId → uuid Set, invalidateAll 용도 | `refresh-token.expiration` (멤버 추가 시 갱신) | **TTL 갱신 추가** |
| `refresh:used:{uuid}` | rotate 완료된 uuid 마커, 재사용 감지 용도 | 5분 | **신규** |

---

## 케이스별 동작

| `refresh:token:{uuid}` | `refresh:used:{uuid}` | 판단 결과 |
|------------------------|----------------------|----------|
| 존재 | - | 정상 토큰 → rotate 진행 |
| 없음 | 있음 | rotate 완료된 토큰 재사용 → `REUSE_DETECTED` + invalidateAll |
| 없음 | 없음 | TTL 만료 or 위조 토큰 → `REFRESH_TOKEN_NOT_FOUND` |

---

## 메서드별 변경 사항

### `create()`

- `opsForSet().add(userKey, uuid)` 후 `expire(userKey, properties.expiration())` 추가
- 다중 기기 로그인 시 Set TTL이 최신 토큰 기준으로 갱신됨

```
변경 전: opsForSet().add(userKey, uuid)
변경 후: opsForSet().add(userKey, uuid)
         expire(userKey, properties.expiration())
```

### `rotate()`

- `opsForSet().remove()` 유지
- `refresh:used:{uuid}` 마커를 TTL 5분으로 저장 추가

```
변경 전: delete(tokenKey) → opsForSet().remove(userKey, uuid) → create()
변경 후: delete(tokenKey) → opsForSet().remove(userKey, uuid) → set(usedKey, "1", 5분) → create()
```

### `handleMissingToken()`

- `opsForSet().isMember()` → `hasKey(usedKey)` 로 교체

```
변경 전: isMember(userKey, uuid) → true면 REUSE_DETECTED
변경 후: hasKey(usedKey)         → true면 REUSE_DETECTED
```

### `invalidateAllTokens()`

- 변경 없음. User Set 기반 전체 무효화 그대로 유지.

---

## 상수 추가

```java
private static final String USED_KEY_PREFIX = "refresh:used:";
private static final Duration USED_TTL = Duration.ofMinutes(5);
```

---

## 테스트 케이스 변경

| 시나리오 | 변경 전 기대값 | 변경 후 기대값 |
|----------|--------------|--------------|
| TTL 만료 후 정상 요청 | `REUSE_DETECTED` (오탐) | `REFRESH_TOKEN_NOT_FOUND` |
| rotate 직후 동일 uuid 재사용 | `REUSE_DETECTED` | `REUSE_DETECTED` (유지) |
| 위조 토큰 | `REFRESH_TOKEN_NOT_FOUND` | `REFRESH_TOKEN_NOT_FOUND` (유지) |
| create() 후 User Set TTL 존재 여부 | TTL 없음 | TTL = expiration |
