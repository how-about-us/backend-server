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

- 커밋 메시지의 한 줄 요약(topic)은 한국어로 작성한다.
- AI 에이전트가 커밋할 때 `Co-Authored-By:` 트레일러를 추가하지 않는다.

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
- PR 본문은 `.github/pull_request_template.md` 형식을 그대로 따른다.
- PR 본문에 변경 이유와 테스트 방법을 간략히 적는다.
- 리뷰 전 로컬에서 `./gradlew build`가 통과해야 한다.

## Code Convention

- Lombok을 적극적으로 활용한다.
- 일반적인 생성자 주입은 `@RequiredArgsConstructor`를 우선 사용한다.
- 구현체가 하나인 경우에는 인터페이스를 만들지 않고 클래스로 직접 정의한다.
- 삼항 연산자는 사용하지 않는다.
- `if`, `for`, `while` 등 제어문의 본문은 한 줄이어도 반드시 `{}`로 감싼다.
- 코드 depth는 2 이하를 원칙으로 하고, 이를 넘길 경우 `guard clause`나 메서드 추출로 우선 단순화한다.
- 코드에서는 FQCN을 직접 쓰지 않고 import를 사용한다.

### 패키지 구조

주요 기능은 도메인별 패키지로 나누고, 설정, 외부 API 연동, 공통 예외 처리처럼 특정 도메인에 속하지 않는 요소는 `common` 아래에 둔다.

```
com.howaboutus.backend.
├── common/
│   ├── config/       ← Security, Jackson, Redis 등 공통 설정
│   ├── error/        ← 공통 예외, 에러 응답, 예외 핸들러
│   └── integration/  ← Google 등 외부 API 연동
└── <domain>/
    ├── controller/   ← Controller, Request/Response DTO
    ├── service/      ← Service, 비즈니스 로직
    ├── repository/   ← Repository 인터페이스
    └── entity/       ← JPA Entity
```

### 예외 처리

- 비즈니스 예외는 커스텀 예외 클래스로 정의하고 `GlobalExceptionHandler`에서 처리한다.
- 별도의 상태 코드와 에러 메시지가 필요한 경우 `CustomException` 클래스를 정의하고, 에러 스펙은 enum으로 관리한다.
- 임의로 `try-catch`를 추가하지 않는다. 처리 방식이 불명확하면 먼저 보고한다.

### 테스트

- 비즈니스 로직(서비스 레이어)은 단위 테스트를 작성한다.
- API 엔드포인트는 기본적으로 통합 테스트(`@SpringBootTest`)로 검증한다.
- 단순한 컨트롤러 레이어 검증은 `@WebMvcTest`를 사용할 수 있다.
- `@WebMvcTest`에서는 모킹된 응답 전체를 세세하게 검증하기보다 상태 코드와 핵심 필드 중심으로 간결하게 검증한다.
- 테스트 메서드에는 반드시 `@DisplayName`을 사용해 테스트 의미를 짧고 명확하게 적는다.
- 테스트 코드의 중복은 공통 필드, `@BeforeEach`, 헬퍼 메서드 등을 활용해 줄인다.
- 테스트 없이 PR을 올리지 않는다. 테스트가 어려운 경우 사유를 PR에 명시한다.
