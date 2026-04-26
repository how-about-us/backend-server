# Rooms 인증 전환 설계

## 개요

현재 `anyRequest().permitAll()`로 열려 있는 보안 설정을 `anyRequest().authenticated()`로 전환하고, RoomController의 `@RequestHeader("X-User-Id")` 방식을 `@AuthenticationPrincipal Long userId`로 교체한다.

## 배경

- SecurityConfig에 `// TODO: API가 갖춰지면 .anyRequest().authenticated()로 전환` 주석이 존재
- RoomController에 `// TODO: X-User-Id 헤더 → @AuthenticationPrincipal로 교체` 주석이 존재
- UserController(`/users/me`)는 이미 `@AuthenticationPrincipal Long userId` 패턴을 사용 중

## 변경 사항

### 1. SecurityConfig

- `.anyRequest().permitAll()` → `.anyRequest().authenticated()`
- `.requestMatchers("/users/me").authenticated()` 제거 (anyRequest에 포함됨)
- TODO 주석 제거
- permitAll 화이트리스트 유지:
  - `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**`
  - `/auth/google/login`, `/auth/refresh`, `/auth/logout`

### 2. RoomController

- 모든 메서드: `@RequestHeader("X-User-Id") Long userId` → `@AuthenticationPrincipal Long userId`
- TODO 주석 제거
- 불필요한 import 제거 (`RequestHeader`)

### 3. RoomControllerTest

- `.header("X-User-Id", USER_ID)` → `access_token` 쿠키 + JwtProvider mock 방식으로 전환
- `JwtProvider.extractUserId(token)` mock 설정 추가

### 4. SecurityConfigTest

- 기존 `/users/me` 401 테스트 유지 (동작 변경 없음)

## 영향 범위

- 인증 없이 접근하던 모든 API가 401을 반환하게 됨
- WebSocket은 아직 미구현이므로 영향 없음
