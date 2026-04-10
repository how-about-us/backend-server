# Contributing Guide

사람과 에이전트가 함께 참고하는 협업 규칙 문서.

## Branch Strategy

| 브랜치 | 용도 |
|--------|------|
| `main` | 배포 기준 브랜치. 직접 push 금지 |
| `feature/<topic>` | 기능 개발, 버그 수정, 설정 변경 등 모든 작업 |

- `main`에 직접 push하지 않는다.
- 브랜치는 작업 단위로 짧게 유지한다.

## Commit Convention

[Conventional Commits](https://www.conventionalcommits.org/) 형식을 따른다.

```
<type>: <한 줄 요약>

[본문 - 선택사항]
```

| type | 사용 시점 |
|------|-----------|
| `feat` | 새 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변경 없는 코드 개선 |
| `docs` | 문서만 변경 |
| `chore` | 빌드, 설정, 의존성 변경 |
| `test` | 테스트 추가 또는 수정 |

## Pull Request Rules

- PR 제목은 커밋 컨벤션과 동일한 형식(`feat: ...`)을 따른다.
- `main`으로 직접 merge 전 최소 1명의 리뷰가 필요하다.
- PR 본문에 변경 이유와 테스트 방법을 간략히 적는다.
- 리뷰 전 로컬에서 `./gradlew build`가 통과해야 한다.

## Code Convention

### 패키지 구조

도메인별로 패키지를 나누고, 각 도메인 안에 MVC + Service 구조를 유지한다.

```
com.howaboutus.backend.
└── <domain>/
    ├── controller/   ← Controller, Request/Response DTO
    ├── service/      ← Service, 비즈니스 로직
    ├── repository/   ← Repository 인터페이스
    └── entity/       ← JPA Entity
```

### 예외 처리

- 비즈니스 예외는 커스텀 예외 클래스로 정의하고 `GlobalExceptionHandler`에서 처리한다.
- 임의로 `try-catch`를 추가하지 않는다. 처리 방식이 불명확하면 먼저 보고한다.

### 테스트

- 비즈니스 로직(서비스 레이어)은 단위 테스트를 작성한다.
- API 엔드포인트는 통합 테스트(`@SpringBootTest`)로 검증한다.
- 테스트 없이 PR을 올리지 않는다. 테스트가 어려운 경우 사유를 PR에 명시한다.
