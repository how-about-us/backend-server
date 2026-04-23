# 내 정보 조회 (`GET /users/me`) Design Spec

**Date:** 2026-04-20
**Status:** Approved

---

## 배경

현재 인증(로그인/로그아웃/토큰 재발급)은 구현되어 있지만, JWT 토큰 검증과 인증 필터가 없어 "로그인된 사용자가 누구인지" 식별할 수 없다. 내 정보 조회 기능을 구현하려면 JWT 검증 → 인증 필터 → SecurityConfig 설정이 선행되어야 한다.

---

## 범위

JWT 토큰 검증, 인증 필터, SecurityConfig 인증 설정, `GET /users/me` 엔드포인트를 한 번에 구현한다.

---

## 1. JwtProvider 토큰 검증

**파일:** `auth/service/JwtProvider.java` (기존 수정)

### 추가 메서드

```
extractUserId(String token) → Long
```

- `Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token)` 으로 파싱
- subject에서 userId 추출, `Long` 반환
- 예외 처리:
  - `ExpiredJwtException` → `ACCESS_TOKEN_EXPIRED` (ErrorCode에 새로 추가)
  - 그 외 JWT 예외 → 기존 `INVALID_TOKEN`

---

## 2. JwtAuthenticationFilter

**파일:** `auth/filter/JwtAuthenticationFilter.java` (신규)

`OncePerRequestFilter` 상속.

### 동작 흐름

1. 요청 쿠키에서 `access_token` 추출
2. 토큰 없음 → 인증 없이 그대로 다음 필터로 통과
3. 토큰 있음 → `JwtProvider.extractUserId()` 호출
4. 성공 → `UsernamePasswordAuthenticationToken(userId, null, emptyList())`을 `SecurityContextHolder`에 세팅
5. 실패(만료/위변조) → 인증 없이 통과 (401은 SecurityConfig 접근 제어에서 처리)

---

## 3. SecurityConfig 수정

**파일:** `common/config/SecurityConfig.java` (기존 수정)

### 변경 사항

1. `JwtAuthenticationFilter`를 `UsernamePasswordAuthenticationFilter` 앞에 등록
2. `/users/me`만 `authenticated()` 설정
3. 나머지는 `permitAll()` 유지
4. `AuthenticationEntryPoint` 설정 — 인증 없이 보호된 엔드포인트 접근 시 기존 에러 형식의 401 JSON 응답
5. TODO 주석: API가 갖춰지면 `anyRequest().authenticated()` 로 전환

---

## 4. 패키지 구조 변경

User 관련 클래스를 `auth/` 에서 `user/` 패키지로 이동:

| 기존 위치 | 이동 위치 |
|-----------|-----------|
| `auth/entity/User.java` | `user/entity/User.java` |
| `auth/repository/UserRepository.java` | `user/repository/UserRepository.java` |

`auth/` 패키지에서 User, UserRepository를 import하는 클래스들의 import 경로 변경.

---

## 5. UserController

**파일:** `user/controller/UserController.java` (신규)

### 엔드포인트

```
GET /users/me
```

- `@AuthenticationPrincipal Long userId` 로 인증된 유저 ID 추출
- `UserService.getMyProfile(userId)` 호출
- `UserResponse` 반환

---

## 6. UserService

**파일:** `user/service/UserService.java` (신규)

### 메서드

```
getMyProfile(Long userId) → UserResponse
```

- `UserRepository.findById(userId)`
- 없으면 → `USER_NOT_FOUND` (ErrorCode에 새로 추가, 404)
- `UserResponse`로 변환 후 반환

---

## 7. UserResponse DTO

**파일:** `user/service/dto/UserResponse.java` (신규)

```java
record UserResponse(
    Long id,
    String email,
    String nickname,
    String profileImageUrl,
    String provider
)
```

---

## 8. ErrorCode 추가

| 코드 | HTTP 상태 | 설명 |
|------|-----------|------|
| `ACCESS_TOKEN_EXPIRED` | 401 | 액세스 토큰 만료 |
| `USER_NOT_FOUND` | 404 | 사용자를 찾을 수 없음 |

---

## API 스펙

| 항목 | 값 |
|------|-----|
| Method | GET |
| Path | `/users/me` |
| 인증 | 필수 (access_token 쿠키) |
| 성공 응답 | 200, `{ id, email, nickname, profileImageUrl, provider }` |
| 에러 | 401 `ACCESS_TOKEN_EXPIRED` / `INVALID_TOKEN`, 404 `USER_NOT_FOUND` |

---

## 문서 갱신

- `docs/ai/features.md`: "내 정보 조회" 상태를 `[x]`로 변경
