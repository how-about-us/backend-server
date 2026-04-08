# INFRA-001: 하네스 리팩토링

## 메타
- 상태: 진행중
- 담당: backend/박주영
- 우선순위: P1
- Notion ID: TBD
- 관련 문서: `docs/harness.md`

## 개요

현재 하네스 구조(AGENTS.md + CLAUDE.md + docs/ai/)를 `docs/harness.md`의 설계안에 맞춰 정리한다. AGENTS.md를 Claude + Codex 공통 진입점으로 확정하고, SDD 워크플로우와 자동화 hook을 단계적으로 도입한다.

---

## 작업 목록

### 1. 지침 파일 정리 (AGENTS.md / CLAUDE.md)

- [ ] **AGENTS.md를 공통 진입점으로 확정**
  - [ ] 현재 `AGENTS.md` 내용 점검 (Tech Stack, Commands, Profiles, Gotchas, Project Docs, Doc Update Rules 섹션 유무 확인)
  - [ ] 300줄 이내 유지 — 초과 내용은 `docs/ai/` 아래로 분리
  - [ ] "Before You Start" / "Before You Finish" 체크리스트 정비
- [ ] **CLAUDE.md를 Claude 전용 오버레이로 축소**
  - [ ] AGENTS.md 내용과 중복되는 부분 제거
  - [ ] `@AGENTS.md` import 구조 확인 (현재 CLAUDE.md가 AGENTS.md를 위임하고 있는지)
  - [ ] Claude 전용 규칙이 생길 때만 추가하는 원칙 명시
- [ ] **역할(페르소나)은 CLAUDE.md가 아닌 skills로 분리** → [INFRA-002](INFRA-002-역할별-skills-구축.md) 참조

### 2. 금지/허용 규칙 정비

- [ ] **금지 규칙 작성 (이유 포함)**
  - [ ] Spring Boot / JPA 관련 금지사항 (예: `open-in-view=true` 금지, EAGER fetch 금지 등) + 이유
  - [ ] 로깅 규칙 (System.out.println 금지 → SLF4J 사용) + 이유
- [ ] **경로별 규칙 정의**
  - [ ] `src/main/resources/` 하위 설정 파일 수정 규칙
  - [ ] 도메인 패키지 구조 규칙
- [ ] **화이트리스트 방식 권한 정의**
  - [ ] 허용 명령어 목록 (gradle 명령, docker compose 명령, git 조회 명령)
  - [ ] 목록 외 작업은 사용자 확인 요청 원칙

### 3. SDD 구조 도입

- [ ] **`docs/specs/` 디렉토리 신설**
  - [ ] `_index.md` 작성 (전체 기능 목록 + 상태 요약표)
  - [ ] spec 파일 템플릿 작성 (`_TEMPLATE.md`) — 메타/개요/요구사항/API연동/DB변경/제약사항 섹션 고정
  - [ ] 기능 ID 체계 확정 (`FE-XXX`, `INFRA-XXX` 등 prefix 정리)
- [ ] **기존 `docs/ai/features.md`를 spec 파일들로 마이그레이션**
  - [ ] features.md의 기능을 개별 spec 파일로 분리
  - [ ] features.md는 인덱스 역할로 축소 또는 `_index.md`로 대체
- [ ] **AGENTS.md에 SDD 워크플로우 섹션 추가**
  - [ ] "기능 개발 시 spec 파일을 먼저 읽는다" 규칙 명시
  - [ ] spec에 없는 기능 임의 추가 금지 규칙

### 4. Hook / 자동화

- [ ] **`.claude/settings.json` 점검**
  - [ ] 현재 설정된 hook 확인
- [ ] **PostToolUse hook 구성**
  - [ ] 편집 후 `./gradlew compileJava` 또는 빠른 검증 명령 실행
  - [ ] 실패 시 Claude에게 피드백 전달하는 스크립트 작성 (`.claude/hooks/post-edit-check.sh`)
- [ ] **커밋/빌드 자동화 hook 검토**
  - [ ] 커밋 전 테스트 실행 여부 결정
  - [ ] 빌드 자동화 범위 결정

### 5. Notion 동기화 전략 결정

- [ ] **동기화 방식 선택**: 수동 vs GitHub Actions 자동화
- [ ] **Notion ID 매핑 규칙 확정** (spec 파일 메타 섹션에 Notion ID 필드)
- [ ] **(선택) GitHub Actions 워크플로우 초안 작성**
  - [ ] `docs/specs/` 변경 감지
  - [ ] Notion API로 상태/체크리스트 동기화

### 6. 하네스 변경 절차 문서화

- [ ] **PR 템플릿에 하네스 변경 체크리스트 추가**
  - [ ] `.github/pull_request_template.md` 생성 또는 수정
  - [ ] AGENTS.md/CLAUDE.md/settings.json 변경 시 체크리스트 강제
- [ ] **정기 리뷰 일정 확정**
  - [ ] 스프린트 회고에 하네스 점검 항목 포함

### 7. 파일 위치 정리

- [x] `docs/하네스 리팩토링 방안.md` → `docs/harness.md` 로 이동 및 개명 (완료)
- [ ] 기타 루트 레벨 문서 위치 재점검

---

## 완료 기준 (DoD)

- AGENTS.md가 300줄 이내이며 Claude/Codex 공통 진입점으로 동작한다
- CLAUDE.md는 AGENTS.md를 위임하며 Claude 전용 규칙만 포함한다
- `docs/specs/` 디렉토리와 최소 1개 이상의 spec 파일이 존재한다
- PostToolUse hook이 동작하여 편집 후 자동 검증이 이루어진다
- PR 템플릿에 하네스 변경 체크리스트가 포함되어 있다

## 제약사항 / 결정사항

- AGENTS.md + CLAUDE.md 병행 사용 (AGENTS.md가 Source of Truth)
- Local Spec → Git → Notion 단방향 흐름 (Notion은 뷰어 역할)
- 지침 파일 300줄 제한 준수
