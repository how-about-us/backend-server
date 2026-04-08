# FE-001-01: 구글 OAuth 로그인

## 메타
- 상태: 대기
- 담당: backend/박주영
- 우선순위: P1
- Notion ID: TBD
- 관련 테이블: `users`

## 개요

Google OAuth 2.0 Authorization Code Flow로 소셜 로그인. 최초 로그인 시 `users` 테이블에 계정을 생성하고, 이후 Access Token + Refresh Token을 발급한다.

---

## API 명세

### `GET /oauth2/authorize/google`
브라우저를 Google 로그인 페이지로 리다이렉트. Spring Security OAuth2 Client가 처리.

### `GET /login/oauth2/code/google` (callback)
Google 인가 코드 수신 → 토큰 교환 → 사용자 정보 조회 → DB upsert → JWT 발급

**Response (redirect)**
```
302 → /oauth2/callback?accessToken={jwt}&refreshToken={token}
```

---

## 구현 노트

- Spring Security OAuth2 Client 사용 (`spring-boot-starter-oauth2-client`)
- `OAuth2UserService` 구현체에서 Google 사용자 정보(`sub`, `email`, `name`, `picture`) 추출
- `users` 테이블 upsert: `google_id`(= `sub`) 기준으로 신규 생성 또는 프로필 갱신
- Access Token: JWT, 만료 15분, `userId` claim 포함
- Refresh Token: UUID, 만료 7일, Redis에 `refresh:{userId}` 키로 저장
- 프론트엔드 callback URL로 리다이렉트하며 쿼리 파라미터로 토큰 전달

---

## DoD

- [ ] 구글 로그인 후 `users` 테이블에 계정이 생성/갱신된다
- [ ] Access Token + Refresh Token이 정상 발급된다
- [ ] 재로그인 시 기존 계정을 재사용한다 (중복 생성 없음)
