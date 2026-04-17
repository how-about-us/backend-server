# Refresh Token Redis 연동 설계

## 개요

Access Token(JWT) 만료 시 재로그인 없이 토큰을 갱신할 수 있도록 Refresh Token을 Redis에 저장하고 관리한다.

## 결정 사항

| 항목 | 결정 |
|---|---|
| Refresh Token 형태 | UUID (opaque string) |
| 전달 방식 | HTTP-only 쿠키 (`path=/auth/refresh`) |
| 갱신 방식 | Silent Refresh + Refresh Token Rotation |
| Replay Detection | 구현 (탈취 의심 시 해당 유저 전체 토큰 무효화) |
| 로그아웃 범위 | 단일 기기 (요청한 Refresh Token만 삭제) |
| Refresh Token TTL | 14일 |
| Access Token 만료 (prod) | 30분 |

## Redis 키 구조

| 키 패턴 | 값 | TTL | 용도 |
|---|---|---|---|
| `refresh:token:{uuid}` | `userId` (String) | 14일 | 토큰 → 유저 매핑, 유효성 검증 |
| `refresh:user:{userId}` | Set\<uuid\> | 없음 (토큰 삭제 시 Set에서도 제거) | 유저의 활성 토큰 목록, 전체 무효화 & Replay Detection |

## Replay Detection 동작

1. Refresh 요청 시 `refresh:token:{uuid}` 존재 여부 확인
2. **존재**: 정상 요청. 해당 토큰 삭제 → 새 토큰 생성 (Rotation)
3. **미존재 + `refresh:user:{userId}` Set에 uuid 존재**: 이미 사용된 토큰 재사용 (탈취 의심) → 해당 유저의 모든 토큰 무효화 → 401
4. **둘 다 없음**: 단순 만료 또는 잘못된 토큰 → 401

## API 엔드포인트

| Method | Path | 설명 | 인증 필요 |
|---|---|---|---|
| POST | `/auth/google/login` | (기존 수정) 로그인 → Access Token + Refresh Token 쿠키 발급 | X |
| POST | `/auth/refresh` | Refresh Token으로 토큰 재발급 (Rotation) | X (Refresh Token 쿠키로 검증) |
| POST | `/auth/logout` | 해당 Refresh Token만 무효화 + 쿠키 삭제 | X (Refresh Token 쿠키로 처리) |

## 플로우

### 로그인 (기존 수정)

```
클라이언트 → POST /auth/google/login {code}
  1. Google OAuth로 유저 확인/생성 (기존 로직)
  2. Access Token(JWT) 생성
  3. Refresh Token(UUID) 생성
  4. Redis 저장: refresh:token:{uuid} → userId, refresh:user:{userId} ← uuid 추가
  5. 응답: Set-Cookie 2개
     - access_token (path=/, maxAge=30분, httpOnly, sameSite=Lax)
     - refresh_token (path=/auth/refresh, maxAge=14일, httpOnly, sameSite=Lax)
```

### Refresh (신규)

```
클라이언트 → POST /auth/refresh (쿠키에 refresh_token 포함)
  1. 쿠키에서 refresh_token 추출
  2. Redis에서 refresh:token:{uuid} 조회
     - 존재 → userId 획득, 해당 토큰 삭제
     - 미존재 → Replay Detection 수행 (탈취 의심 시 전체 무효화) → 401
  3. 새 Access Token + 새 Refresh Token 생성
  4. Redis에 새 토큰 저장, Set 업데이트
  5. 응답: Set-Cookie 2개 (새 토큰들)
```

### 로그아웃 (신규)

```
클라이언트 → POST /auth/logout (쿠키에 refresh_token 포함)
  1. 쿠키에서 refresh_token 추출
  2. refresh:token:{uuid} 삭제
  3. refresh:user:{userId} Set에서 해당 uuid 제거
  4. 응답: Set-Cookie 2개 (maxAge=0으로 쿠키 삭제)
```

## 쿠키 설정

| 쿠키 | path | maxAge | httpOnly | sameSite |
|---|---|---|---|---|
| `access_token` | `/` | 30분 (prod) | true | Lax |
| `refresh_token` | `/auth/refresh` | 14일 | true | Lax |

## 에러 처리

| 상황 | HTTP Status | 설명 |
|---|---|---|
| Refresh Token 쿠키 없음 | 401 | 쿠키 누락 |
| Refresh Token 만료/미존재 | 401 | Redis에 키 없음 |
| Replay Detection 감지 | 401 | 해당 유저 전체 토큰 무효화 후 401 |
| 로그아웃 시 유효하지 않은 토큰 | 204 | 멱등성 보장 (이미 없어도 쿠키 삭제 처리) |

## 컴포넌트 구조

### 신규 생성

| 파일 | 역할 |
|---|---|
| `auth/service/RefreshTokenService.java` | Refresh Token 생성, 검증, Rotation, Replay Detection, 삭제 (Redis 연동 핵심 로직) |
| `common/config/properties/RefreshTokenProperties.java` | Refresh Token TTL 설정값 (`@ConfigurationProperties`) |

### 기존 수정

| 파일 | 변경 내용 |
|---|---|
| `auth/controller/AuthController.java` | `/auth/refresh`, `/auth/logout` 엔드포인트 추가. 로그인 응답에 Refresh Token 쿠키 추가 |
| `auth/service/AuthService.java` | 로그인 시 RefreshTokenService 호출. Refresh/Logout 비즈니스 로직 |
| `auth/service/JwtProvider.java` | Access Token 만료시간 환경별 분리 (prod 30분) |
| `common/config/SecurityConfig.java` | `/auth/refresh`, `/auth/logout` 경로 permitAll 추가 |
| `application.yaml` | `refresh-token.expiration` 설정 추가 |
| `application-prod.yaml` | Access Token 만료시간 30분 설정 |

### 변경하지 않는 것

- `CacheConfig.java`: Refresh Token은 캐시가 아닌 세션 저장소 용도이므로 `StringRedisTemplate` 직접 사용
- `CachePolicy.java`: 동일 사유로 추가하지 않음

## 테스트 전략

- **단위 테스트**: `RefreshTokenService`의 생성/검증/Rotation/Replay Detection/삭제 로직
- **통합 테스트**: 실제 Redis 연동으로 로그인 → Refresh → 로그아웃 전체 플로우 검증
