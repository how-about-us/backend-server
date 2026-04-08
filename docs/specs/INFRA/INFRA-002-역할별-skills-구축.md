# INFRA-002: 역할별 Skills 구축

## 메타
- 상태: 대기
- 담당: backend/박주영
- 우선순위: P2
- Notion ID: TBD
- 관련 문서: `docs/harness.md`, `.claude/skills/`

## 개요

CLAUDE.md에 단일 페르소나를 박아두는 대신, **역할별로 `.claude/skills/`에 Skill을 분리**한다. Agent가 작업 맥락(리뷰 / 구현 / 디버깅 / 스키마 설계 등)에 맞는 skill을 선택적으로 로드하는 구조로 가면, CLAUDE.md는 얇게 유지하면서 상황별 전문성은 극대화할 수 있다.

현재 `.claude/skills/senior-backend-review/`가 이미 존재하므로, 이를 기준으로 역할 체계를 확장한다.

---

## 작업 목록

### 1. Skill 분류 체계 정의

- [ ] **역할(Role) 카테고리 확정**
  - [ ] `senior-backend-review` — 코드 리뷰 (✅ 이미 존재)
  - [ ] `backend-implementer` — 기능 구현 (Spring Boot / JPA 기본 원칙 반영)
  - [ ] `db-schema-designer` — ERD / 마이그레이션 / PostGIS 설계
  - [ ] `api-designer` — REST / WebSocket / STOMP 엔드포인트 설계
  - [ ] `test-writer` — 단위/통합 테스트 작성 원칙
  - [ ] `debugger` — 장애/버그 원인 분석
- [ ] **Skill 네이밍 규칙 정의** (kebab-case, 역할 중심)
- [ ] **Skill 활성화 시점 정의** (언제 어떤 skill을 트리거할지 — description 필드 작성 기준)

### 2. 공통 Skill 템플릿 작성

- [ ] `.claude/skills/_TEMPLATE/SKILL.md` 템플릿 생성
  - [ ] frontmatter: `name`, `description`, `allowed-tools` 등
  - [ ] 섹션: 역할 / 원칙 / 체크리스트 / 하지 말 것 / 참고 문서
- [ ] 기존 `senior-backend-review/SKILL.md`를 템플릿에 맞게 정렬

### 3. 역할별 Skill 작성

각 skill은 다음을 포함한다:
- **페르소나** (예: "대규모 트래픽 백엔드 엔지니어")
- **원칙** (이유 포함)
- **체크리스트** (작업 전/후)
- **금지 사항** (이유 포함)
- **참고할 프로젝트 문서** (`docs/ai/api-spec.md` 등)

- [ ] **`backend-implementer`**
  - [ ] N+1 쿼리 경계, fetch 전략 원칙
  - [ ] `open-in-view=false` 전제 하에 트랜잭션 경계 설계
  - [ ] DTO ↔ Entity 분리 원칙
  - [ ] Lombok 사용 규칙
- [ ] **`db-schema-designer`**
  - [ ] PostGIS 공간 타입 사용 규칙 (`hibernate-spatial`)
  - [ ] 마이그레이션 파일 네이밍 / 작성 원칙
  - [ ] 인덱스 설계 체크리스트
  - [ ] `docs/ai/erd.md` 동기화 의무
- [ ] **`api-designer`**
  - [ ] REST 응답 포맷 통일 규칙
  - [ ] HTTP 상태 코드 사용 원칙
  - [ ] WebSocket/STOMP 엔드포인트 설계 규칙 (Security 설정 포함)
  - [ ] rate limiting 고려 사항
  - [ ] `docs/ai/api-spec.md` 동기화 의무
- [ ] **`test-writer`**
  - [ ] 단위 테스트 vs 통합 테스트 구분 기준
  - [ ] `@SpringBootTest` 남용 금지 + 이유
  - [ ] Testcontainers 사용 시점
- [ ] **`debugger`**
  - [ ] 로그 확인 → 가설 수립 → 최소 재현 순서 강제
  - [ ] 추측 기반 수정 금지

### 4. CLAUDE.md 연동

- [ ] **CLAUDE.md에서 페르소나 직접 정의 제거**
- [ ] **"언제 어떤 skill을 쓸지" 힌트만 남기기**
  - 예: "코드 리뷰 시 `senior-backend-review` skill 사용"
  - 예: "신규 API 설계 시 `api-designer` skill 사용"
- [ ] Skills 우선 원칙 명시 (CLAUDE.md의 일반 규칙보다 skill의 도메인 규칙이 우선)

### 5. 검증

- [ ] 각 skill이 독립적으로 동작하는지 테스트
- [ ] 실제 작업 시나리오로 skill 트리거 확인
  - [ ] PR 리뷰 요청 → `senior-backend-review` 자동 호출되는지
  - [ ] API 추가 작업 → `api-designer` 호출되는지
- [ ] skill description이 명확해서 Claude가 올바르게 선택하는지 확인

---

## 완료 기준 (DoD)

- `.claude/skills/` 아래 최소 5개 이상의 역할별 skill이 존재한다
- CLAUDE.md에는 페르소나 정의가 없고, skill 사용 안내만 있다
- 각 skill은 공통 템플릿을 따르며, 관련 프로젝트 문서와 연결되어 있다
- 실제 작업에서 skill이 의도한 시점에 트리거된다

## 제약사항 / 결정사항

- 페르소나는 CLAUDE.md가 아닌 `.claude/skills/`에서 관리
- 기존 `senior-backend-review` skill을 기준 레퍼런스로 사용
- Skill 추가/변경도 하네스 변경 절차(PR 리뷰)를 따른다
