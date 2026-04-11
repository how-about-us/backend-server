# 20260409-1659-self-hosted-runner-deploy

## Status

확정

## Context

- 운영 서버 배포 자동화를 위해 기존 `ssh`/`scp` 기반 GitHub Actions 워크플로우를 유지할지, `self-hosted runner` 또는 `watchtower` 방식으로 전환할지 결정해야 했다.
- 현재 배포는 GitHub Actions에서 이미지를 빌드/푸시한 뒤, 원격 서버에 Compose 파일을 복사하고 `docker compose pull && up -d`를 실행하는 구조였다.
- 배포 이력은 GitHub Actions에 남기되, 원격 접속 의존성과 서버 측 SSH 설정 부담은 줄이고 싶었다.

## Decision

- 이미지 빌드와 푸시는 GitHub-hosted runner에서 유지하고, 실제 배포는 운영 서버에 설치한 `self-hosted runner`가 로컬에서 `docker compose`를 실행하는 방식으로 변경한다.
- `watchtower`는 적용하지 않는다.

## Consequences

- 장점
  - `ssh`/`scp` 액션 의존성을 제거하고 GitHub Actions job 안에서 배포를 직접 수행할 수 있다.
  - 기존의 SHA 기반 이미지 태그 전략과 배포 이력 추적 방식을 그대로 유지할 수 있다.
  - 배포 시점과 실행 로그를 GitHub Actions에서 명시적으로 통제할 수 있다.
- 단점 및 제약
  - 운영 서버에 GitHub Actions `self-hosted runner`를 설치하고 유지해야 한다.
  - runner 실행 계정이 Docker 명령을 실행할 수 있어야 한다.
  - `watchtower` 대비 초기 설정은 더 많다.
- 후속 범위
  - `.github/workflows/deploy-compose.yml`에서 `deploy` job을 `self-hosted` runner 기반으로 유지한다.
  - 운영 서버의 runner 라벨, 설치 경로, 권한 정책은 실제 인프라 환경에 맞춰 점검한다.

## Related Docs

- `.github/workflows/deploy-compose.yml`
- `compose.app.prod.yaml`
