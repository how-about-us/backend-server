# how-about-us-backend

이 저장소의 공통 AI 작업 안내 문서.

`Claude Code`, `Codex` 등 에이전트가 함께 사용할 공통 프로젝트 컨텍스트는 이 파일에 유지한다.

## Source Of Truth

- `AGENTS.md`는 공통 작업 안내와 문서 인덱스를 관리하는 기준 문서다.
- 기능 요구사항, API, DB 구조 같은 세부 도메인 사실은 `docs/ai/` 아래 문서를 기준으로 확인한다.
- `CLAUDE.md`에는 Claude 전용 메모만 둔다.
- 세부 설명이 길어지면 별도 문서로 분리하고, 이 파일에는 "언제 어떤 문서를 읽어야 하는지"만 짧게 적는다.

## Before You Start

- 이번 작업이 기능, API, DB 중 어디에 영향을 주는지 먼저 식별한다.
- 필요한 문서만 골라 읽는다. 기능은 `docs/ai/features.md`, DB는 `docs/ai/erd.md`를 우선 확인한다.
- 구조적 선택, 정책 확정, 대안 비교 결과가 중요한 작업이면 `docs/ai/decisions/README.md`도 확인한다.
- 문서와 코드, 테스트, 사용자 최신 요청 사이에 불일치가 있으면 추정으로 진행하지 말고, 사용자에게 불일치 내용을 보고한 뒤 어느 쪽을 기준으로 할지 확인하고 진행한다.
- 구현 전에 `Doc Update Rules`를 기준으로 작업 후 어떤 문서를 함께 갱신해야 하는지 미리 정한다.

## Tech Stack

- **Framework**: Spring Boot 4.0.5, Java 21
- **Database**: PostgreSQL 17 + PostGIS 3.5
- **Cache**: Redis 8
- **Auth**: Spring Security
- **Realtime**: WebSocket + STOMP
- **Build**: Gradle
- **기타**: Lombok, Spring Data JPA, `hibernate-spatial`

## Commands

### 로컬 개발

```bash
# dev 프로파일로 실행
# Spring Boot의 spring.docker.compose.enabled 기능으로 Docker Compose(PostgreSQL, Redis)가 자동으로 함께 실행됨
# 실행 전 .env.dev 파일이 필요함
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### 빌드 / 테스트

```bash
./gradlew build
./gradlew test
./gradlew bootJar
```

### Docker Compose 수동 제어

```bash
docker compose -f compose.yaml -f compose.dev.yaml up -d
docker compose -f compose.yaml -f compose.dev.yaml down
```

## Profiles

| 프로파일 | 용도 | 설명 |
| --- | --- | --- |
| `dev` | 로컬 개발 | Docker Compose 자동 start/stop, PostgreSQL 포트 `5433` |
| `prod` | AWS Lightsail 배포 | 외부 DB/Redis 연결, `application-prod.yaml` 적용 |

## Architecture

```text
src/main/java/com/howaboutus/backend/
└── (도메인별 패키지 구성)

src/main/resources/
├── application.yaml
├── application-dev.yaml
└── application-prod.yaml
```

## Gotchas

- PostgreSQL 이미지는 `postgis/postgis:17-3.5`를 사용한다.
- 공간 데이터 엔티티에는 `hibernate-spatial` 타입을 사용한다.
- dev 환경의 PostgreSQL 포트는 `5433`이다.
- `spring.jpa.open-in-view=false`가 설정되어 있다.
- WebSocket + STOMP 사용 시 Spring Security 설정에서 WebSocket 엔드포인트를 별도 허용해야 한다.

## Multi-Doc Guidance

- 상세 설명 문서는 `docs/ai/` 아래에 분리하는 것을 권장한다.
- 이 파일에서는 긴 내용을 직접 복붙하지 말고, 필요한 문서 경로와 읽는 조건만 적는다.
- `@path/to/file.md` 문법은 사용할 수 있어도 즉시 파일을 불러와 컨텍스트를 크게 사용할 수 있으니, 작은 핵심 문서에만 제한적으로 사용한다.
- 큰 문서나 참고성 문서는 `docs/ai/erd.md` 같은 일반 경로 표기로 남기는 편이 안전하다.

## Project Docs

| 문서 | 경로 | 읽는 시점 |
|------|------|-----------|
| 기능 명세서 | `docs/ai/features.md` | 구현할 기능 파악 또는 미결 사항 확인 시 |
| ERD 명세서 | `docs/ai/erd.md` | 테이블 구조, 컬럼, 관계 파악 시 |
| 결정 기록 가이드 | `docs/ai/decisions/README.md` | 중요한 설계 선택의 배경, 결정, 영향 범위를 확인하거나 새 결정 기록을 남길 때 |
| 협업 규칙 | `CONTRIBUTING.md` | 브랜치 전략, 커밋 컨벤션, PR 규칙, 코드 규칙 확인 시 |

## Doc Update Rules

- 기능 요구사항이나 정책을 변경했으면 `docs/ai/features.md`를 함께 갱신한다.
- 테이블, 컬럼, 관계, 제약조건을 변경했으면 `docs/ai/erd.md`를 함께 갱신한다.
- 중요한 설계 선택이나 구조적 결정이 확정되었으면 `docs/ai/decisions/`에 결정 기록을 남긴다.

## Agent Boundaries

- **자율 가능**: 파일 읽기/수정, 로컬 빌드·테스트 실행, 커밋
- **확인 필요**: push, PR 생성, 의존성 추가, DB 스키마 변경
- **금지**: `main` 직접 push, prod 환경 설정 변경

## Before You Finish

- 코드 변경 내용이 관련 문서에 반영되었는지 확인한다.
- 새로 생긴 미결 사항은 관련 문서에 `미결` 또는 결정 기록으로 남긴다.
- 확정되지 않은 내용은 `예상안`, `초안`, `미결`처럼 상태가 드러나게 표시한다.
- 커밋 메시지의 한 줄 요약(topic)은 한국어로 작성한다.
- 수행한 검증 방법과 아직 검증하지 못한 범위를 분리해서 설명한다.
