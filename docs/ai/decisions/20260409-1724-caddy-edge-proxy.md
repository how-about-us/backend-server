# 20260409-1724-caddy-edge-proxy

## Status

확정

## Context

- 운영 환경에서 API 서버를 직접 `8080` 포트로 노출하기보다, HTTPS 종료와 리버스 프록시를 담당하는 엣지 프록시 레이어가 필요했다.
- 후보는 `nginx`와 `Caddy`였고, 현재 운영 환경은 단일 서버 기반이며 소규모 서비스에 맞는 단순한 운영 구성이 우선순위였다.
- 이 프로젝트는 OAuth2와 STOMP over WebSocket을 사용하므로 HTTPS와 프록시 헤더 처리, WebSocket 프록시 지원이 필요하다.

## Decision

- 운영 엣지 프록시로 `Caddy`를 사용한다.
- 프로덕션에서는 `app`과 `caddy`를 하나의 Compose 파일에서 함께 운영한다.

## Consequences

- 장점
  - 자동 HTTPS와 인증서 갱신을 기본 기능으로 활용할 수 있어 운영 복잡도가 낮다.
  - WebSocket 프록시 설정이 단순하고, 단일 서버 환경에서 빠르게 배치할 수 있다.
  - 배포 파일 수와 배포 절차가 줄어들어 운영 단순성이 높다.
- 단점 및 제약
  - `APP_DOMAIN`이 `.env.prod`에 반드시 정의되어 있어야 한다.
  - 앱과 프록시가 같은 Compose 단위로 묶이므로 배포 경계 분리는 낮다.
  - 세밀한 프록시 정책이나 복잡한 location 제어는 `nginx`보다 단순한 수준으로 시작한다.
- 후속 범위
  - `compose.app.prod.yaml`은 `app`과 `caddy`를 함께 정의하고, 앱은 내부 `8080`만 `expose`한다.
  - `infra/caddy/Caddyfile`를 운영 서버에 함께 배치한다.
  - 배포 워크플로우는 단일 Compose 파일 기준으로 관련 파일만 서버에 복사한다.

## Related Docs

- `compose.app.prod.yaml`
- `infra/caddy/Caddyfile`
- `.github/workflows/deploy-compose.yml`
